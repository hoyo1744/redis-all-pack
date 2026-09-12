package com.study.redisallpack.domain.coupon

import com.study.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "coupon")
class Coupon protected constructor(
    couponId: String,
    name: String,
    totalQuantity: Int,
    issuedQuantity: Int,
    lastFence: Long,
) : BaseEntity() {

    @Column(name = "coupon_id", nullable = false, unique = true, length = 64)
    var couponId: String = couponId
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int = totalQuantity
        protected set

    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Int = issuedQuantity
        protected set

    /**
     * 지금까지 받아들인 가장 큰 펜싱 토큰.
     *
     * **락이 아니라 자원 쪽에 있는 방어선이다.** 락은 자기가 만료된 걸 모르는 프로세스를 못 막지만,
     * 여기서 번호를 비교하면 그 지각 쓰기가 튕긴다.
     */
    @Column(name = "last_fence", nullable = false)
    var lastFence: Long = lastFence
        protected set

    val remaining: Int get() = totalQuantity - issuedQuantity

    /**
     * 락 없이 이걸 동시에 부르면 **발급 수량이 총량을 넘는다.**
     * 1주차는 그 초과 발급을 재현하는 데서 시작한다.
     */
    fun issue() {
        check(remaining > 0) { "쿠폰이 모두 소진되었습니다. couponId=$couponId" }

        issuedQuantity += 1
    }

    /**
     * 펜싱 토큰을 확인하고 발급한다.
     *
     * 락을 잡은 순서대로 토큰이 커지므로, **작거나 같은 토큰은 이미 지난 차례**다.
     * GC STW 나 네트워크 지연으로 뒤늦게 도착한 쓰기가 여기서 걸린다.
     *
     * ```
     * A: 토큰 5 획득 ── STW 12s ──────────────────▶ issue(5)  ← lastFence=6 이라 거부
     * B:               TTL 만료 · 토큰 6 획득 ─▶ issue(6)     ← 통과, lastFence=6
     * ```
     *
     * ⚠️ **이건 직렬화가 아니다.** 순서가 뒤집힌 쓰기를 거부할 뿐, 동시 진입 자체는 못 막는다.
     * 락을 대체하는 게 아니라 락이 흘린 것을 받는 마지막 그물이다.
     */
    fun issue(fence: Long) {
        if (fence <= lastFence) {
            throw StaleFenceException(couponId, fence, lastFence)
        }

        check(remaining > 0) { "쿠폰이 모두 소진되었습니다. couponId=$couponId" }

        lastFence = fence
        issuedQuantity += 1
    }

    companion object {
        fun of(couponId: String, name: String, totalQuantity: Int) =
            Coupon(couponId, name, totalQuantity, issuedQuantity = 0, lastFence = 0)
    }
}

/** 이미 지난 차례의 쓰기가 도착했다. 막힌 것 자체가 성과라 사고가 아니라 지표다. */
class StaleFenceException(
    val couponId: String,
    val fence: Long,
    val lastFence: Long,
) : RuntimeException("낡은 펜싱 토큰입니다. couponId=$couponId 토큰=$fence 마지막=$lastFence")
