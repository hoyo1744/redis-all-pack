package com.study.redisallpack.infrastructure.coupon

import com.study.redisallpack.domain.coupon.Coupon
import com.study.redisallpack.domain.coupon.CouponRepository
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {

    override fun save(coupon: Coupon): Coupon = couponJpaRepository.save(coupon)

    override fun findByCouponId(couponId: String): Coupon? =
        couponJpaRepository.findByCouponId(couponId)

    override fun findLatestByCouponId(couponId: String): Coupon? =
        couponJpaRepository.findByCouponIdForUpdate(couponId)

    override fun issueIfAvailable(couponId: String): Int =
        couponJpaRepository.issueIfAvailable(couponId)

    override fun issueIfAvailable(couponId: String, fence: Long): Int =
        couponJpaRepository.issueIfAvailable(couponId, fence)
}
