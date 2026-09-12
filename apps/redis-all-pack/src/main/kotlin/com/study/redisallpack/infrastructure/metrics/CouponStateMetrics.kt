package com.study.redisallpack.infrastructure.metrics

import com.study.redisallpack.domain.coupon.Coupon
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * **DB 에 실제로 기록된 발급 수량**을 지표로 낸다.
 *
 * 요청 지표(`coupon.issue`)만으로는 이 실험의 핵심이 안 보인다. 그건 *"몇 명이 성공 응답을 받았나"*
 * 이고, 우리가 봐야 하는 건 *"실제로 몇 장이 나갔나"* 다. **둘의 차이가 곧 허수**이고,
 * 락이 깨졌을 때 드러나는 피해가 바로 그 간극이다.
 *
 * ```
 * HTTP 200 누적  −  coupon_issued  =  존재하지 않는 쿠폰을 받았다고 믿는 사람 수
 * ```
 *
 * ⚠️ **쿠폰 ID 를 태그로 쓰므로 카디널리티가 늘어난다.** 실험용 쿠폰이 계속 생기는 프로젝트라
 * 상한을 둔다. 운영이라면 이렇게 하면 안 된다 — 태그 값이 무한히 늘면 Prometheus 가 메모리를 먹는다.
 */
@Component
class CouponStateMetrics(
    private val registry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val issued = ConcurrentHashMap<String, AtomicInteger>()
    private val remaining = ConcurrentHashMap<String, AtomicInteger>()
    private val total = ConcurrentHashMap<String, AtomicInteger>()

    fun record(coupon: Coupon) {
        if (issued.size >= MAX_TRACKED && !issued.containsKey(coupon.couponId)) {
            return
        }

        gauge(issued, ISSUED, coupon.couponId).set(coupon.issuedQuantity)
        gauge(remaining, REMAINING, coupon.couponId).set(coupon.remaining)
        gauge(total, TOTAL, coupon.couponId).set(coupon.totalQuantity)
    }

    private fun gauge(
        holder: ConcurrentHashMap<String, AtomicInteger>,
        name: String,
        couponId: String,
    ): AtomicInteger = holder.computeIfAbsent(couponId) { id ->
        AtomicInteger().also {
            Gauge.builder(name, it) { v -> v.get().toDouble() }
                .tag("couponId", id)
                .register(registry)

            if (holder.size == MAX_TRACKED) {
                log.warn("[지표] 추적 쿠폰이 상한 {} 에 도달했습니다. 이후 쿠폰은 지표에 안 잡힙니다.", MAX_TRACKED)
            }
        }
    }

    private companion object {
        const val ISSUED = "coupon.issued"
        const val REMAINING = "coupon.remaining"
        /**
         * ⚠️ `coupon.total` 로 두면 안 된다. Prometheus 에서 **`_total` 은 카운터 전용 접미사**라
         * 게이지에 붙이면 정규화 과정에서 노출되지 않는다. 실제로 이 이름으로 만들었다가 지표가 사라졌다.
         */
        const val TOTAL = "coupon.capacity"

        /** 실험용 쿠폰이 계속 쌓이므로 상한을 둔다. */
        const val MAX_TRACKED = 200
    }
}
