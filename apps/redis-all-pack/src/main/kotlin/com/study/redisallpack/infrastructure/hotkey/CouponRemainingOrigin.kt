package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.coupon.CouponService
import com.study.redisallpack.domain.hotkey.RemainingOrigin
import org.springframework.stereotype.Component

/** [RemainingOrigin] 을 DB 조회(CouponService)로 잇는다. */
@Component
class CouponRemainingOrigin(
    private val couponService: CouponService,
) : RemainingOrigin {

    override fun read(eventId: String): Int = couponService.getRemaining(eventId)
}
