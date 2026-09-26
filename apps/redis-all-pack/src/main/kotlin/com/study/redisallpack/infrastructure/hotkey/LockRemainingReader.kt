package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingOrigin
import com.study.redisallpack.domain.hotkey.RemainingReader
import com.study.redisallpack.infrastructure.metrics.StampedeMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 순진한 대안 ② — 분산 락 기반 재계산.
 *
 * 만료로 미스가 나면 `SET NX` 로 락을 잡은 **하나만** 원본(DB)을 재계산하고, 못 잡은 요청은
 * 승자가 캐시를 채울 때까지 **잠깐 기다렸다 다시 읽는다.** 원본 호출은 만료당 1 회로 눌린다.
 *
 * ⚠️ 대가가 있다. (1) 만료 순간 모든 요청이 **락 키 하나로 몰려** 그 키가 새 핫스팟이 되고,
 * (2) 락을 못 잡은 요청은 **대기**해서 지연이 는다. 스탬피드를 락 경합으로 바꿔친 것이다.
 * PER 은 이 락 자체를 없앤다.
 */
@Component
class LockRemainingReader(
    private val redisTemplate: StringRedisTemplate,
    private val origin: RemainingOrigin,
    private val metrics: StampedeMetrics,
    @Value("\${hotkey.stampede.ttl-millis:1000}") private val ttlMillis: Long,
    @Value("\${hotkey.stampede.lock-wait-millis:200}") private val waitMillis: Long,
) : RemainingReader {

    override val strategy = HotKeyStrategy.LOCK

    override fun read(eventId: String): Int? {
        cached(eventId)?.let { return it }                       // 유효하면 그대로

        // 미스 → 락 경쟁
        val gotLock = redisTemplate.opsForValue()
            .setIfAbsent(lockKey(eventId), "1", Duration.ofMillis(ttlMillis))
        if (gotLock == true) return recompute(eventId)          // 승자만 DB

        // 패자 → 승자가 채울 때까지 잠깐 기다렸다 재조회
        val deadline = System.currentTimeMillis() + waitMillis
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
            cached(eventId)?.let { return it }
        }
        return recompute(eventId)                                // 끝내 못 받으면 폴백 (드물어야 정상)
    }

    override fun write(eventId: String, remaining: Int) {
        redisTemplate.opsForValue().set(key(eventId), remaining.toString(), Duration.ofMillis(ttlMillis))
    }

    override fun evict(eventId: String) {
        redisTemplate.delete(key(eventId))
        redisTemplate.delete(lockKey(eventId))
    }

    private fun cached(eventId: String): Int? =
        redisTemplate.opsForValue().get(key(eventId))?.toIntOrNull()

    private fun recompute(eventId: String): Int {
        val value = origin.read(eventId)
        metrics.recordRecompute(strategy)
        redisTemplate.opsForValue().set(key(eventId), value.toString(), Duration.ofMillis(ttlMillis))
        redisTemplate.delete(lockKey(eventId))
        return value
    }

    private companion object {
        fun key(eventId: String) = "event:remaining:lock:$eventId"
        fun lockKey(eventId: String) = "lock:remaining:$eventId"
    }
}
