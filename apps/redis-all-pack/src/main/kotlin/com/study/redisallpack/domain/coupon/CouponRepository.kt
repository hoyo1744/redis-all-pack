package com.study.redisallpack.domain.coupon

interface CouponRepository {

    fun save(coupon: Coupon): Coupon

    fun findByCouponId(couponId: String): Coupon?
}
