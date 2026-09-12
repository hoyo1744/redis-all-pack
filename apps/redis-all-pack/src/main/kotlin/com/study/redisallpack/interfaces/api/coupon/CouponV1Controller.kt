package com.study.redisallpack.interfaces.api.coupon

import com.study.redisallpack.application.coupon.CouponCriteria
import com.study.redisallpack.application.coupon.CouponFacade
import com.study.redisallpack.domain.coupon.WriteMode
import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.lock.LockType
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

    /**
     * 부하를 때릴 지점. `k6/coupon-issue.js` 가 여기를 두드린다.
     *
     * 축을 **쿼리 파라미터**로 받는다. 재시작 없이 갈아끼워야
     * JIT 예열과 커넥션 풀 상태가 같은 조건으로 유지된다.
     *
     * ```
     * ?lock=NONE                        기준선 — lost update 재현
     * ?lock=NONE&write=ATOMIC           락 없이 조건부 UPDATE 만
     * ?lock=REDISSON&write=PLAIN        락만
     * ?lock=REDISSON&write=FENCED       락 + 펜싱
     * ?lock=REDISSON&write=FENCED&pause=1000
     *                                   임계 구역에서 1 초 멈춘다 (GC STW 흉내)
     *                                   lease 보다 길면 지각 쓰기가 발생한다
     * ```
     */
    @PostMapping("/{couponId}/issue")
    fun issue(
        @PathVariable couponId: String,
        @RequestParam(name = "lock", required = false) lock: LockType?,
        @RequestParam(name = "write", required = false) write: WriteMode?,
        @RequestParam(name = "pause", required = false) pause: Long?,
        @RequestBody request: CouponV1Dto.IssueRequest,
    ) = couponFacade.issue(CouponCriteria.Issue.of(couponId, request.userId, lock, write, pause))

    /**
     * 잔여 수량 조회. 2주차 핫키 실험 지점이다.
     *
     * ```
     * ?hot=NONE           완화 없음 — 한 슬롯에 집중
     * ?hot=LOCAL_CACHE    앱 메모리에서 답 — Redis 왕복이 사라진다
     * ?hot=KEY_SHARDING   키를 N 개로 쪼개 슬롯을 흩는다
     * ```
     */
    @GetMapping("/{couponId}/remaining")
    fun remaining(
        @PathVariable couponId: String,
        @RequestParam(name = "hot", required = false) hot: String?,
    ) = couponFacade.getRemaining(couponId, HotKeyStrategy.from(hot))

    /** 4주차 MGET 비교. `?couponIds=a,b,c` */
    @GetMapping("/remainings")
    fun remainings(@RequestParam couponIds: List<String>) = couponFacade.getRemainings(couponIds)

    /** 지금 어떤 락을 쓸 수 있고 lease 가 얼마인지. 측정 결과 해석에 필요하다. */
    @GetMapping("/settings")
    fun settings() = couponFacade.settings()
}
