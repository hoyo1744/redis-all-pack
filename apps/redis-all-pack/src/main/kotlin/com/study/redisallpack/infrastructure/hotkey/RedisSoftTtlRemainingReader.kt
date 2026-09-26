package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingOrigin
import com.study.redisallpack.domain.hotkey.RemainingReader
import com.study.redisallpack.infrastructure.metrics.StampedeMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisSoftTtlRemainingReader(
    private val redisTemplate: StringRedisTemplate,
    private val origin: RemainingOrigin,
    private val metrics: StampedeMetrics,
    @Value("\${hotkey.stampede.ttl-millis:1000}") private val softMillis: Long,
    @Value("\${hotkey.soft-ttl.hard-extra-millis:1000}") private val hardExtraMillis: Long,
    /** 콜드 미스 때 못 잡은 요청이 승자를 기다리는 상한. */
    @Value("\${hotkey.soft-ttl.lock-wait-millis:200}") private val waitMillis: Long,
) : RemainingReader {

    override val strategy = HotKeyStrategy.REDIS_SOFT_TTL

    override fun read(eventId: String): Int? {
        val entry = load(eventId) ?: return coldRecompute(eventId)   // 콜드 미스 → 락+대기
        val now = System.currentTimeMillis()

        if (now < entry.soft) return entry.value                     // fresh
        if (now >= entry.hard) return coldRecompute(eventId)         // 하드 만료 → 값 없는 셈, 락+대기

        // 소프트~하드: SET NX 로 이긴 하나만 DB 갱신. ⚠️ 재계산이 예외나도 락은 반드시 푼다.
        if (tryLock(eventId)) {
            try {
                recompute(eventId)
            } finally {
                redisTemplate.delete(lockKey(eventId))
            }
        }
        return entry.value                                           // 나머지는 낡은 값
    }

    /**
     * 콜드 미스·하드 만료: 낡은 값조차 없으므로 [read] 의 "즉시 stale 반환"을 못 쓴다.
     * SET NX 로 **하나만** DB 를 치고, 못 잡은 요청은 승자가 채울 때까지 **잠깐 대기**했다 재조회한다.
     * (soft 창의 leak 이 아니라 콜드 스타트 스탬피드를 막는 경로다.)
     */
    private fun coldRecompute(eventId: String): Int {
        if (tryLock(eventId)) {
            try {
                return recompute(eventId)
            } finally {
                redisTemplate.delete(lockKey(eventId))
            }
        }
        val deadline = System.currentTimeMillis() + waitMillis
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
            load(eventId)?.let { if (System.currentTimeMillis() < it.hard) return it.value }
        }
        return recompute(eventId)                                    // 끝내 못 받으면 폴백 (드물어야 정상)
    }

    override fun write(eventId: String, remaining: Int) {
        store(eventId, remaining)
    }

    override fun evict(eventId: String) {
        redisTemplate.delete(key(eventId))
        redisTemplate.delete(lockKey(eventId))
    }

    /** SET NX 로 갱신 권한을 하나만 잡는다. soft 창(softMillis) 동안 유효. */
    private fun tryLock(eventId: String): Boolean =
        redisTemplate.opsForValue()
            .setIfAbsent(lockKey(eventId), "1", Duration.ofMillis(softMillis)) == true

    private fun recompute(eventId: String): Int {
        val value = origin.read(eventId)
        metrics.recordRecompute(strategy)
        store(eventId, value)
        return value
    }

    private fun store(eventId: String, remaining: Int) {
        val now = System.currentTimeMillis()
        val entry = Entry(remaining, now + softMillis, now + softMillis + hardExtraMillis)
        redisTemplate.opsForValue().set(
            key(eventId),
            "${entry.value}|${entry.soft}|${entry.hard}",
            Duration.ofMillis(softMillis + hardExtraMillis + softMillis),   // 물리 TTL > hard
        )
    }

    private fun load(eventId: String): Entry? {
        val p = redisTemplate.opsForValue().get(key(eventId))?.split('|') ?: return null
        if (p.size != 3) return null
        val value = p[0].toIntOrNull() ?: return null
        val soft = p[1].toLongOrNull() ?: return null
        val hard = p[2].toLongOrNull() ?: return null
        return Entry(value, soft, hard)
    }

    private data class Entry(val value: Int, val soft: Long, val hard: Long)

    private companion object {
        fun key(eventId: String) = "event:remaining:softttl:$eventId"
        fun lockKey(eventId: String) = "softttl:lock:$eventId"
    }
}
