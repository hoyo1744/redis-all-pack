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

    val remaining: Int get() = totalQuantity - issuedQuantity

    /**
     * 락 없이 이걸 동시에 부르면 **발급 수량이 총량을 넘는다.**
     * 1주차는 그 초과 발급을 재현하는 데서 시작한다.
     */
    fun issue() {
        check(remaining > 0) { "쿠폰이 모두 소진되었습니다. couponId=$couponId" }

        issuedQuantity += 1
    }

    companion object {
        fun of(couponId: String, name: String, totalQuantity: Int) =
            Coupon(couponId, name, totalQuantity, issuedQuantity = 0)
    }
}
