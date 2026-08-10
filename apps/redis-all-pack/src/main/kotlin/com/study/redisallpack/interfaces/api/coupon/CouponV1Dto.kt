package com.study.redisallpack.interfaces.api.coupon

class CouponV1Dto {

    data class RegisterRequest(val couponId: String, val name: String, val totalQuantity: Int)

    data class IssueRequest(val userId: String)
}
