package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * 순진한 대안 ① — TTL 지터. 짧은 TTL 에 난수를 더해 만료 시각을 흩는다.
 *
 * 읽기는 그냥 Redis 조회다. 미스면 `null` 을 돌려주고 **상위 계층이 DB 를 친다** — 만료 순간
 * 도착한 모든 요청이 동시에 그렇게 하므로, 이 전략은 스탬피드를 **막지 못한다.**
 *
 * ⚠️ 지터는 원래 **여러 키가 동시에 만료**되는 걸 흩는 기법이다. 키가 하나면 만료 시각만
 * 옮길 뿐 그 한 순간의 떼몰림은 그대로다. 측정에서 NONE 과 거의 같게 나오는 게 정상이다.
 */
@Component
class JitterRemainingReader(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${hotkey.stampede.ttl-millis:1000}") private val ttlMillis: Long,
    @Value("\${hotkey.stampede.jitter-ratio:0.1}") private val jitterRatio: Double,
) : RemainingReader {

    override val strategy = HotKeyStrategy.JITTER

    override fun read(eventId: String): Int? =
        redisTemplate.opsForValue().get(key(eventId))?.toIntOrNull()

    override fun write(eventId: String, remaining: Int) {
        val jitter = ThreadLocalRandom.current().nextLong((ttlMillis * jitterRatio).toLong() + 1)
        redisTemplate.opsForValue().set(key(eventId), remaining.toString(), Duration.ofMillis(ttlMillis + jitter))
    }

    override fun evict(eventId: String) {
        redisTemplate.delete(key(eventId))
    }

    private companion object {
        fun key(eventId: String) = "event:remaining:jitter:$eventId"
    }
}
