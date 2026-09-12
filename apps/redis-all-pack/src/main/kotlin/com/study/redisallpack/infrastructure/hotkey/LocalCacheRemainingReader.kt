package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 앱 메모리에서 먼저 답한다. 맞으면 **Redis 왕복이 아예 없다.**
 *
 * 핫키 대응 중 가장 효과가 크다. 슬롯을 옮기는 게 아니라 **트래픽이 Redis 에 닿기 전에 끊기** 때문이다.
 *
 * ⚠️ 대가는 **낡은 값**이다. 로컬 TTL 만큼 각 인스턴스가 서로 다른 값을 들고 있을 수 있고,
 * 무효화가 인스턴스마다 따로 논다. 잔여 수량처럼 "조금 틀려도 되는" 조회에만 쓸 수 있다.
 */
@Component
class LocalCacheRemainingReader(
    private val delegate: PlainRemainingReader,
    /** 짧게 둘수록 낡음이 줄고 왕복이 늘어난다. 이 값이 곧 트레이드오프의 손잡이다. */
    @Value("\${hotkey.local-cache-millis:1000}") private val ttlMillis: Long,
) : RemainingReader {

    override val strategy = HotKeyStrategy.LOCAL_CACHE

    private val cache = ConcurrentHashMap<String, Entry>()

    override fun read(eventId: String): Int? {
        val now = System.nanoTime()
        val hit = cache[eventId]

        if (hit != null && now < hit.expiresAt) return hit.value

        // 로컬에 없거나 만료됐으면 Redis 로 간다. 여기서만 왕복이 발생한다.
        val fromRedis = delegate.read(eventId) ?: return null
        cache[eventId] = Entry(fromRedis, now + ttlMillis * 1_000_000)

        return fromRedis
    }

    override fun write(eventId: String, remaining: Int) {
        delegate.write(eventId, remaining)
        cache[eventId] = Entry(remaining, System.nanoTime() + ttlMillis * 1_000_000)
    }

    /** ⚠️ 이 인스턴스의 캐시만 지운다. 다른 인스턴스는 TTL 이 끝날 때까지 낡은 값을 본다. */
    override fun evict(eventId: String) {
        delegate.evict(eventId)
        cache.remove(eventId)
    }

    private data class Entry(val value: Int, val expiresAt: Long)
}
