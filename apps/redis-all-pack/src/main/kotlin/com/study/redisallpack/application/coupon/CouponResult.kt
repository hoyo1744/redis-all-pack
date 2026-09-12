package com.study.redisallpack.application.coupon

import com.study.redisallpack.domain.coupon.Coupon
import com.study.redisallpack.domain.coupon.WriteMode
import com.study.redisallpack.domain.lock.LockType

class CouponResult {

    /**
     * 어떤 락 · 어떤 쓰기 방식 · 몇 번째 토큰이었는지 함께 돌려준다.
     * 부하 도구가 응답만 보고 집계할 수 있어야 매번 DB 를 뒤지지 않는다.
     */
    data class Issued(
        val couponId: String,
        val userId: String,
        val issuedQuantity: Int,
        val remaining: Int,
        val lockType: LockType,
        val writeMode: WriteMode,
        val fence: Long,
    ) {
        companion object {
            fun of(coupon: Coupon, userId: String, lockType: LockType, writeMode: WriteMode, fence: Long) = Issued(
                couponId = coupon.couponId,
                userId = userId,
                issuedQuantity = coupon.issuedQuantity,
                remaining = coupon.remaining,
                lockType = lockType,
                writeMode = writeMode,
                fence = fence,
            )
        }
    }

    data class Remaining(val couponId: String, val remaining: Int, val fromCache: Boolean)
}
