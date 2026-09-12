package com.study.redisallpack.infrastructure.metrics

import com.study.redisallpack.domain.coupon.StaleFenceException
import com.study.redisallpack.domain.coupon.WriteMode
import com.study.redisallpack.domain.lock.LockAcquisitionException
import com.study.redisallpack.domain.lock.LockType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

/**
 * 발급 요청을 **결과별로** 센다. 대시보드에서 눈으로 확인하려면 이 태그가 있어야 한다.
 *
 * ⚠️ **결과를 뭉치면 아무것도 안 보인다.** 락을 못 잡아 못 받은 것 · 소진돼서 못 받은 것 ·
 * 지각 쓰기가 막힌 것은 완전히 다른 일이다. 특히 `stale_fence` 는 **락이 뚫렸다는 증거**라
 * 다른 실패와 섞이면 그 신호가 사라진다.
 *
 * `lock` 과 `write` 를 태그로 두는 이유는 요청마다 축을 바꿔가며 재기 때문이다.
 * 재시작 없이 갈아끼우므로 한 대시보드에서 네 락을 나란히 볼 수 있다.
 */
@Component
class CouponMetrics(
    private val registry: MeterRegistry,
) {

    fun <T> recordIssue(lock: LockType, write: WriteMode, couponId: String, block: () -> T): T {
        val sample = Timer.start(registry)
        var outcome = ISSUED

        try {
            return block()
        } catch (e: LockAcquisitionException) {
            outcome = LOCK_TIMEOUT
            throw e
        } catch (e: StaleFenceException) {
            // 락이 뚫려 지각 쓰기가 도착했다. 0 이 아니면 그 자체로 사건이다.
            outcome = STALE_FENCE
            throw e
        } catch (e: IllegalStateException) {
            outcome = SOLD_OUT
            throw e
        } catch (e: Exception) {
            outcome = ERROR
            throw e
        } finally {
            // 쿠폰별 응답 수. 히스토그램이 아니라 카운터라 카디널리티 부담이 작다.
            // DB 게이지(coupon_issued)와 이 값의 차이가 곧 허수다.
            Counter.builder(RESPONSE_COUNTER)
                .tag("couponId", couponId)
                .tag("outcome", outcome)
                .register(registry)
                .increment()

            sample.stop(
                Timer.builder(ISSUE_TIMER)
                    .tag("lock", lock.name)
                    .tag("write", write.name)
                    .tag("outcome", outcome)
                    .register(registry),
            )
        }
    }

    private companion object {
        const val ISSUE_TIMER = "coupon.issue"
        const val RESPONSE_COUNTER = "coupon.issue.responses"

        const val ISSUED = "issued"
        const val SOLD_OUT = "sold_out"
        const val LOCK_TIMEOUT = "lock_timeout"
        const val STALE_FENCE = "stale_fence"
        const val ERROR = "error"
    }
}
