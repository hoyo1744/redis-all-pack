package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.DistributedLock
import com.study.redisallpack.domain.lock.Fence
import com.study.redisallpack.domain.lock.FenceGenerator
import com.study.redisallpack.domain.lock.LockAcquisitionException
import com.study.redisallpack.domain.lock.LockType
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * 가장 단순한 분산락. `SET key token NX PX ttl` 하나가 전부다.
 *
 * **NX 가 없으면 락이 아니다.** `SET` 만 쓰면 남이 잡은 락을 덮어쓰기 때문이다.
 * **PX 가 없어도 락이 아니다.** 잡은 프로세스가 죽으면 아무도 못 푸는 키가 영원히 남는다.
 *
 * ⚠️ **해제는 DEL 이 아니라 비교 후 DEL 이어야 한다.**
 * 내 작업이 TTL 보다 오래 걸리면 락은 이미 남에게 넘어가 있다.
 * 그 상태에서 `DEL` 하면 **남의 락을 푼다.** 그래서 토큰을 확인하고 지운다.
 * 확인과 삭제 사이가 벌어지면 안 되므로 Lua 로 한 번에 보낸다.
 *
 * 비교 대상으로서의 성격 — **기다리는 비용을 요청 스레드가 낸다.**
 * 락이 안 풀리는 동안 [BACKOFF_MILLIS] 마다 Redis 를 한 번씩 더 친다.
 * 경합이 심할수록 왕복이 선형으로 늘어나고, 그동안 톰캣 스레드는 붙잡혀 있다.
 * [RedissonLock] 이 pub/sub 으로 눕는 것과 여기서 갈린다.
 */
@Component
class SpinLock(
    private val redisTemplate: StringRedisTemplate,
    private val fenceGenerator: FenceGenerator,
) : DistributedLock {

    private val log = LoggerFactory.getLogger(javaClass)

    override val type = LockType.SPIN

    override fun <T> withLock(key: String, wait: Duration, lease: Duration, block: (Fence) -> T): T {
        val token = UUID.randomUUID().toString()
        val deadline = System.nanoTime() + wait.toNanos()

        var attempts = 0

        while (true) {
            attempts++

            if (tryAcquire(key, token, lease)) {
                log.debug("[스핀락] 획득 key={} 시도={}", key, attempts)

                return try {
                    block(Fence(fenceGenerator.next(key), lease))
                } finally {
                    release(key, token)
                }
            }

            if (System.nanoTime() >= deadline) {
                throw LockAcquisitionException(key, type, "락 대기 시간을 넘겼습니다. key=$key 시도=$attempts")
            }

            // 지터로 재시도 시점을 흩는다.
            Thread.sleep(BACKOFF_MILLIS + ThreadLocalRandom.current().nextLong(JITTER_MILLIS))
        }
    }

    /** `SET NX PX` 는 원자적이다. GET 으로 확인하고 SET 하면 그 사이에 남이 잡는다. */
    private fun tryAcquire(key: String, token: String, lease: Duration): Boolean =
        redisTemplate.opsForValue().setIfAbsent(key, token, lease) == true

    /**
     * 내 토큰일 때만 지운다.
     *
     * 반환값이 0 이면 **이미 내 락이 아니었다** — TTL 이 먼저 끝났다는 뜻이다.
     * 조용히 넘기면 안 된다. 그 순간 임계 구역에 둘이 있었을 수 있고, 그게 펜싱이 필요한 이유다.
     */
    private fun release(key: String, token: String) {
        val released = redisTemplate.execute(UNLOCK_SCRIPT, listOf(key), token)

        if (released == 0L) {
            log.warn("[스핀락] 해제 실패 — 이미 내 락이 아닙니다. key={} (TTL 초과 의심)", key)
        }
    }

    companion object {
        private const val BACKOFF_MILLIS = 5L
        private const val JITTER_MILLIS = 5L

        private val UNLOCK_SCRIPT = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """.trimIndent(),
            Long::class.java,
        )
    }
}
