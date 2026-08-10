package com.study.redisallpack.infrastructure.coupon

import com.study.redisallpack.domain.coupon.Coupon
import org.springframework.data.jpa.repository.JpaRepository

interface CouponJpaRepository : JpaRepository<Coupon, Long> {

    fun findByCouponId(couponId: String): Coupon?
}
