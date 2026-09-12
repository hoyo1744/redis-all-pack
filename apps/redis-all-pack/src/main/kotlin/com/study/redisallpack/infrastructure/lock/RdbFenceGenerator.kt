package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.FenceGenerator
import com.study.redisallpack.domain.lock.FenceSequence
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

/**
 * RDB 낙관적 락으로 펜싱 토큰을 발급한다. 읽고 → 조건부로 쓰고 → 어긋나면 재시도.
 *
 * `INCR` 과 달리 재시도가 붙지만 **되감기지 않는다.** 커밋된 트랜잭션은 사라지지 않기 때문이다.
 *
 * ⚠️ 메서드 전체를 트랜잭션으로 묶으면 안 된다. REPEATABLE READ 라 재조회가 같은 스냅샷을 돌려줘
 * 재시도가 무한 루프가 된다.
 */
@Component
@Qualifier(FENCE_DELEGATE)
@ConditionalOnProperty(name = ["coupon.fence.source"], havingValue = "rdb")
class RdbFenceGenerator(
    private val repository: FenceSequenceJpaRepository,
) : FenceGenerator {

    private val log = LoggerFactory.getLogger(javaClass)

    override val source = "rdb-optimistic"

    override fun next(key: String): Long {
        repeat(MAX_ATTEMPTS) { attempt ->
            val current = repository.findByFenceKey(key) ?: create(key)

            if (repository.bumpIfUnchanged(key, current.token) > 0) {
                return current.token + 1
            }

            if (attempt > 0) Thread.sleep(ThreadLocalRandom.current().nextLong(BACKOFF_MILLIS))
        }

        throw IllegalStateException("펜싱 토큰 발급 경합이 $MAX_ATTEMPTS 회 내에 해소되지 않았습니다. key=$key")
    }

    /** 첫 호출이면 행이 없다. 동시 생성은 unique 제약에 걸리는데, 그건 남이 만들어줬다는 뜻이다. */
    private fun create(key: String): FenceSequence =
        runCatching { repository.saveAndFlush(FenceSequence.of(key)) }
            .getOrElse {
                if (it !is DataIntegrityViolationException) throw it

                log.debug("[펜스] 시퀀스 생성 경합 key={} — 남이 먼저 만들었다", key)

                repository.findByFenceKey(key)
                    ?: throw IllegalStateException("펜스 시퀀스를 만들지도 찾지도 못했습니다. key=$key")
            }

    private companion object {
        const val MAX_ATTEMPTS = 50
        const val BACKOFF_MILLIS = 3L
    }
}
