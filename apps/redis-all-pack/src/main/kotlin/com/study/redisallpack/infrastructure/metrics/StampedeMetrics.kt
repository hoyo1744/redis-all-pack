package com.study.redisallpack.infrastructure.metrics

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * **원본(DB) 재계산 횟수**를 전략별로 센다. 캐시 스탬피드의 핵심 지표다.
 *
 * 만료 순간 이 값이 치솟으면 떼몰림이 DB 를 때린 것이다. 그래프에서:
 * - NONE / JITTER : 만료마다 **봉우리** (단일 키라 지터는 못 막는다)
 * - LOCK          : 만료마다 **1** (대신 못 잡은 요청은 대기)
 * - SOFT_TTL      : 인스턴스 수만큼 **계단**
 * - PER           : **평평한 바닥**
 *
 * Prometheus 로 `cache_origin_recompute_total{strategy=...}` 로 나간다. `_total` 은
 * Micrometer 가 붙이므로 여기서 이름에 넣지 않는다.
 */
@Component
class StampedeMetrics(
    private val registry: MeterRegistry,
) {
    fun recordRecompute(strategy: HotKeyStrategy) {
        Counter.builder(RECOMPUTE)
            .tag("strategy", strategy.name)
            .register(registry)
            .increment()
    }

    private companion object {
        const val RECOMPUTE = "cache.origin.recompute"
    }
}
