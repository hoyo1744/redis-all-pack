package com.study.redisallpack.interfaces.api.coupon

import com.study.redisallpack.application.coupon.CouponCriteria
import com.study.redisallpack.application.coupon.CouponFacade
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class CouponV1Controller(
    private val couponFacade: CouponFacade,
) {

    @PostMapping
    fun register(@RequestBody request: CouponV1Dto.RegisterRequest) =
        couponFacade.register(
            CouponCriteria.Register(request.couponId, request.name, request.totalQuantity),
        )

    /** 부하를 때릴 지점. `k6/lock-contention.js` 가 여기를 두드린다. */
    @PostMapping("/{couponId}/issue")
    fun issue(
        @PathVariable couponId: String,
        @RequestBody request: CouponV1Dto.IssueRequest,
    ) = couponFacade.issue(CouponCriteria.Issue.of(couponId, request.userId))

    @GetMapping("/{couponId}/remaining")
    fun remaining(@PathVariable couponId: String) = couponFacade.getRemaining(couponId)

    /** 4주차 MGET 비교. `?couponIds=a,b,c` */
    @GetMapping("/remainings")
    fun remainings(@RequestParam couponIds: List<String>) = couponFacade.getRemainings(couponIds)
}
