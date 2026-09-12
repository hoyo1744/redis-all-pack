package com.study.redisallpack.domain.coupon

interface CouponRepository {

    fun save(coupon: Coupon): Coupon

    fun findByCouponId(couponId: String): Coupon?

    /** 최신 커밋본을 읽는다. 조건부 UPDATE 가 왜 0 행이었는지 가를 때만 쓴다. */
    fun findLatestByCouponId(couponId: String): Coupon?

    /** @return 영향 행 수. 0 이면 소진. */
    fun issueIfAvailable(couponId: String): Int

    /** @return 영향 행 수. 0 이면 소진이거나 토큰이 낡았다. */
    fun issueIfAvailable(couponId: String, fence: Long): Int
}
