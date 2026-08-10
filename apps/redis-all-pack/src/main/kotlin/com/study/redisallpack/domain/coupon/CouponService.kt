package com.study.redisallpack.domain.coupon

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    private val couponRepository: CouponRepository,
) {

    @Transactional
    fun issue(couponId: String): Coupon = getOrThrow(couponId).apply { issue() }

    @Transactional(readOnly = true)
    fun getRemaining(couponId: String): Int = getOrThrow(couponId).remaining

    @Transactional
    fun register(couponId: String, name: String, totalQuantity: Int): Coupon =
        couponRepository.save(Coupon.of(couponId, name, totalQuantity))

    private fun getOrThrow(couponId: String): Coupon =
        couponRepository.findByCouponId(couponId)
            ?: throw NoSuchElementException("존재하지 않는 쿠폰입니다. couponId=$couponId")
}
