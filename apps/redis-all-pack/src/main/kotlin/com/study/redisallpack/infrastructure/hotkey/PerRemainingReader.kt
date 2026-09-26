package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingOrigin
import com.study.redisallpack.domain.hotkey.RemainingReader
import com.study.redisallpack.infrastructure.metrics.StampedeMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.ln

@Component
class PerRemainingReader(
    private val redisTemplate: StringRedisTemplate,
    private val origin: RemainingOrigin,
    private val metrics: StampedeMetrics,
    @Value("\${hotkey.per.beta:1.0}") private val beta: Double,
    @Value("\${hotkey.per.ttl-millis:1000}") private val ttlMillis: Long,
    @Value("\${hotkey.per.cold-wait-millis:200}") private val waitMillis: Long,
) : RemainingReader {

    override val strategy = HotKeyStrategy.PER

    override fun read(eventId: String): Int? {
        val entry = load(eventId) ?: return coldRecompute(eventId)   // 진짜 미스 → 락+대기

        val now = System.currentTimeMillis()
        val gap = (entry.delta * beta * -ln(ThreadLocalRandom.current().nextDouble())).toLong()

        return if (now + gap >= entry.expiry) recompute(eventId) else entry.value
    }

    override fun write(eventId: String, remaining: Int) {
        recompute(eventId)
    }

    override fun evict(eventId: String) {
        redisTemplate.delete(key(eventId))
        redisTemplate.delete(coldLockKey(eventId))
    }

    private fun coldRecompute(eventId: String): Int {
        if (tryColdLock(eventId)) {
            try {
                return recompute(eventId)
            } finally {
                redisTemplate.delete(coldLockKey(eventId))
            }
        }
        val deadline = System.currentTimeMillis() + waitMillis
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
            load(eventId)?.let { return it.value }
        }
        return recompute(eventId)                                    // 끝내 못 받으면 폴백 (드물어야 정상)
    }

    private fun tryColdLock(eventId: String): Boolean =
        redisTemplate.opsForValue()
            .setIfAbsent(coldLockKey(eventId), "1", Duration.ofMillis(ttlMillis)) == true

    private fun recompute(eventId: String): Int {
        val started = System.currentTimeMillis()
        val value = origin.read(eventId)
        metrics.recordRecompute(strategy)
        val delta = (System.currentTimeMillis() - started).coerceAtLeast(1)

        val entry = Entry(value, delta, started + ttlMillis)
        redisTemplate.opsForValue().set(
            key(eventId),
            encode(entry),
            Duration.ofMillis(ttlMillis + ttlMillis / 2),   // 물리 TTL > 논리 만료
        )
        return value
    }

    private fun load(eventId: String): Entry? =
        redisTemplate.opsForValue().get(key(eventId))?.let(::decode)

    private fun encode(e: Entry) = "${e.value}|${e.delta}|${e.expiry}"

    private fun decode(raw: String): Entry? {
        val p = raw.split('|')
        if (p.size != 3) return null
        val value = p[0].toIntOrNull() ?: return null
        val delta = p[1].toLongOrNull() ?: return null
        val expiry = p[2].toLongOrNull() ?: return null
        return Entry(value, delta, expiry)
    }

    private data class Entry(val value: Int, val delta: Long, val expiry: Long)

    private companion object {
        fun key(eventId: String) = "event:remaining:per:$eventId"
        fun coldLockKey(eventId: String) = "per:coldlock:$eventId"
    }
}
