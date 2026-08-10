package com.study.redisallpack.application.coupon

import com.study.redisallpack.domain.coupon.CouponCacheRepository
import com.study.redisallpack.domain.coupon.CouponService
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 선착순 쿠폰 발급. **여기가 모든 주차의 실험 대상이다.**
 *
 * 지금은 락이 없다. 동시에 때리면 발급 수량이 총량을 넘는 걸 먼저 확인하고,
 * 1주차부터 락을 하나씩 끼워 넣으며 무엇이 달라지는지 본다.
 */
@Component
class CouponFacade(
    private val couponService: CouponService,
    private val couponCacheRepository: CouponCacheRepository,
) {

    fun issue(criteria: CouponCriteria.Issue): CouponResult.Issued {
        val coupon = couponService.issue(criteria.couponId)

        // 발급했으면 캐시가 낡는다. 6주차에서 이 무효화가 스탬피드의 방아쇠가 된다.
        couponCacheRepository.evict(criteria.couponId)

        return CouponResult.Issued.of(coupon, criteria.userId)
    }

    /** 캐시 미스면 DB 로 간다. 6주차에서 이 경로에 지터 · softTTL · PER 을 끼운다. */
    fun getRemaining(couponId: String): CouponResult.Remaining {
        couponCacheRepository.getRemaining(couponId)?.let {
            return CouponResult.Remaining(couponId, it, fromCache = true)
        }

        val remaining = couponService.getRemaining(couponId)
        couponCacheRepository.putRemaining(couponId, remaining, CACHE_TTL)

        return CouponResult.Remaining(couponId, remaining, fromCache = false)
    }

    /** 4주차 MGET 비교용. */
    fun getRemainings(couponIds: List<String>): Map<String, Int> =
        couponCacheRepository.getRemainings(couponIds)

    fun register(criteria: CouponCriteria.Register) =
        couponService.register(criteria.couponId, criteria.name, criteria.totalQuantity)
            .let { CouponResult.Remaining(it.couponId, it.remaining, fromCache = false) }

    companion object {
        private val CACHE_TTL: Duration = Duration.ofSeconds(30)
    }
}
