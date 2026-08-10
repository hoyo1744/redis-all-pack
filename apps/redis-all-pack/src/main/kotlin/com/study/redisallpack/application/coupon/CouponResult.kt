package com.study.redisallpack.application.coupon

import com.study.redisallpack.domain.coupon.Coupon

class CouponResult {

    data class Issued(
        val couponId: String,
        val userId: String,
        val issuedQuantity: Int,
        val remaining: Int,
    ) {
        companion object {
            fun of(coupon: Coupon, userId: String) = Issued(
                couponId = coupon.couponId,
                userId = userId,
                issuedQuantity = coupon.issuedQuantity,
                remaining = coupon.remaining,
            )
        }
    }

    data class Remaining(val couponId: String, val remaining: Int, val fromCache: Boolean)
}
