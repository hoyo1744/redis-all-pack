package com.study.redisallpack.application.coupon

class CouponCriteria {

    data class Issue(val couponId: String, val userId: String) {
        companion object {
            fun of(couponId: String, userId: String) = Issue(couponId, userId)
        }
    }

    data class Register(val couponId: String, val name: String, val totalQuantity: Int)
}
