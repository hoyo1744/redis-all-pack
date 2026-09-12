package com.study.redisallpack.infrastructure.coupon

import com.study.redisallpack.domain.coupon.Coupon
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponJpaRepository : JpaRepository<Coupon, Long> {

    fun findByCouponId(couponId: String): Coupon?

    /** ⚠️ 잠금 읽기여야 한다. REPEATABLE READ 스냅샷은 남이 방금 올린 last_fence 를 못 본다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.couponId = :couponId")
    fun findByCouponIdForUpdate(@Param("couponId") couponId: String): Coupon?

    /** DB 가 현재 값을 보고 더하므로 lost update 가 성립하지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Coupon c
           SET c.issuedQuantity = c.issuedQuantity + 1
         WHERE c.couponId = :couponId
           AND c.issuedQuantity < c.totalQuantity
        """,
    )
    fun issueIfAvailable(@Param("couponId") couponId: String): Int

    /** `last_fence < :fence` 가 지각 쓰기를 튕기는 조건절이다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Coupon c
           SET c.lastFence = :fence,
               c.issuedQuantity = c.issuedQuantity + 1
         WHERE c.couponId = :couponId
           AND c.lastFence < :fence
           AND c.issuedQuantity < c.totalQuantity
        """,
    )
    fun issueIfAvailable(@Param("couponId") couponId: String, @Param("fence") fence: Long): Int
}
