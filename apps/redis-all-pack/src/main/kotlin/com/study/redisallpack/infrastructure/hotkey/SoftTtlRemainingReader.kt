package com.study.redisallpack.infrastructure.hotkey

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * soft TTL(stale-while-revalidate). [PerRemainingReader] 의 사촌이다 — 둘 다 만료 순간의
 * 동시 미스를 없애지만 철학이 다르다.
 *
 * - **soft TTL**: 소프트 만료라는 *경계*를 둔다. 넘긴 첫 요청이 뒤에서 갱신을 돌리고,
 *   그동안 나머지는 **낡은 값을 즉시** 받는다. 반응형.
 * - **PER**: 경계 없이 만료가 가까울수록 확률적으로 **미리** 갱신한다. 예방형.
 *
 * Caffeine `refreshAfterWrite` 가 정확히 soft TTL 이다. 리프레시는 키당 하나만 도는
 * 싱글플라이트라, 소프트 만료 순간에도 갱신은 **한 번**만 나간다.
 *
 * ⚠️ 두 가지가 PER 과 다르다.
 * 1. 갱신 중엔 **낡은 값**을 알면서 내보낸다(설계상).
 * 2. 로컬(JVM) 싱글플라이트라 인스턴스 사이는 못 합친다 — 소프트 만료 경계에서
 *    인스턴스마다 하나씩, 총 N 번 갱신이 나갈 수 있다. 전역 1 회로 만들려면 락이 필요하다.
 *    그 지점을 락 없이 넘는 게 PER 이다.
 */
@Component
class SoftTtlRemainingReader(
    private val delegate: PlainRemainingReader,
    /** 소프트 만료: 이 시간이 지나면 다음 접근이 백그라운드 갱신을 트리거한다. */
    @Value("\${hotkey.soft-ttl.refresh-millis:800}") refreshMillis: Long,
    /** 하드 만료: 이 시간이 지나면 진짜로 축출된다. 소프트보다 넉넉히 둔다. */
    @Value("\${hotkey.soft-ttl.expire-millis:5000}") expireMillis: Long,
) : RemainingReader {

    override val strategy = HotKeyStrategy.SOFT_TTL

    private val cache: LoadingCache<String, Int> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .refreshAfterWrite(Duration.ofMillis(refreshMillis))   // 소프트 만료 → 뒤에서 갱신
        .expireAfterWrite(Duration.ofMillis(expireMillis))     // 하드 만료 → 축출
        .build { eventId -> delegate.read(eventId) }           // 리프레시 로더 (Redis 조회)

    override fun read(eventId: String): Int? = cache.get(eventId)

    override fun write(eventId: String, remaining: Int) {
        delegate.write(eventId, remaining)
        cache.put(eventId, remaining)
    }

    /** ⚠️ 이 인스턴스의 로컬 캐시만 지운다. 다른 인스턴스는 하드 만료까지 낡은 값을 본다. */
    override fun evict(eventId: String) {
        delegate.evict(eventId)
        cache.invalidate(eventId)
    }
}
