package com.study.redisallpack.application.coupon

import com.study.redisallpack.domain.coupon.WriteMode
import com.study.redisallpack.domain.lock.LockType

class CouponCriteria {

    /**
     * @param lockType 어떤 락으로 잡을지. 기본 [LockType.NONE] — **기준선부터 재고 시작한다.**
     * @param writeMode 어떻게 쓸지. 락과 직교하는 축이라 따로 받는다.
     * @param pauseMillis 임계 구역 안에서 멈출 시간. **GC STW 흉내다.**
     *   lease 보다 크게 주면 락이 만료된 채로 쓰기가 나가 지각 쓰기를 재현할 수 있다.
     */
    data class Issue(
        val couponId: String,
        val userId: String,
        val lockType: LockType = LockType.NONE,
        val writeMode: WriteMode = WriteMode.PLAIN,
        val pauseMillis: Long = 0,
    ) {
        companion object {
            fun of(
                couponId: String,
                userId: String,
                lockType: LockType?,
                writeMode: WriteMode?,
                pauseMillis: Long?,
            ) = Issue(
                couponId = couponId,
                userId = userId,
                lockType = lockType ?: LockType.NONE,
                writeMode = writeMode ?: WriteMode.PLAIN,
                pauseMillis = (pauseMillis ?: 0).coerceAtLeast(0),
            )
        }
    }

    data class Register(val couponId: String, val name: String, val totalQuantity: Int)
}
