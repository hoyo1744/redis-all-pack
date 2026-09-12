package com.study.redisallpack.infrastructure.hotkey

import com.github.benmanes.caffeine.cache.Caffeine
import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * [LocalCacheRemainingReader] 의 실무 버전. 세 가지가 공짜로 따라온다.
 *
 * 1. **스탬피드 방지** — `get(key, loader)` 가 같은 키의 동시 미스를 한 번의 로드로 합친다.
 *    손제작 캐시는 만료 순간 대기 중이던 요청이 전부 Redis 로 몰려간다(실측 ~1,870회).
 *    카페인은 한 스레드만 가고 나머지는 그 결과를 기다린다(실측 ~110회).
 * 2. **크기 제한 + 축출** — 손제작 `ConcurrentHashMap` 은 만료돼도 안 지워져 힙에 쌓인다.
 * 3. **만료 관리** — `Entry(value, expiresAt)` 손검사가 통째로 사라진다.
 *
 * ⚠️ `expireAfterAccess` 를 쓰면 안 된다. 핫키는 접근이 끊이지 않아 타이머가 계속 리셋되고,
 * **영원히 만료되지 않는다.** 쓴 시점 기준(`expireAfterWrite`)이어야 갱신이 보장된다.
 */
@Component
class CaffeineRemainingReader(
    private val delegate: PlainRemainingReader,
    @Value("\${hotkey.local-cache-millis:1000}") ttlMillis: Long,
) : RemainingReader {

    override val strategy = HotKeyStrategy.CAFFEINE

    private val cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMillis(ttlMillis))
        .build<String, Int>()

    /**
     * 동시 미스 합치기가 여기서 일어난다. loader 는 동시 호출 묶음당 한 번만 실행된다.
     * loader 가 null 을 돌려주면 카페인은 **캐시하지 않고 null 을 반환**하므로,
     * Redis 미스는 저장되지 않고 호출자가 DB 로 가서 채운다.
     */
    override fun read(eventId: String): Int? =
        cache.get(eventId) { delegate.read(it) }

    override fun write(eventId: String, remaining: Int) {
        delegate.write(eventId, remaining)
        cache.put(eventId, remaining)
    }

    /** ⚠️ 이 인스턴스의 캐시만 지운다. 다른 인스턴스는 TTL 이 끝날 때까지 낡은 값을 본다. */
    override fun evict(eventId: String) {
        delegate.evict(eventId)
        cache.invalidate(eventId)
    }
}
