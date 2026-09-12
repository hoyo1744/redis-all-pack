package com.study.redisallpack.domain.coupon

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    private val couponRepository: CouponRepository,
) {

    /**
     * [WriteMode] 에 따라 쓰기 방식을 고른다. **락과 직교하는 축이라 따로 받는다.**
     *
     * @param fence 펜싱 토큰. [WriteMode.FENCED] 일 때만 쓰인다.
     */
    @Transactional
    fun issue(couponId: String, mode: WriteMode, fence: Long): Coupon = when (mode) {
        WriteMode.PLAIN -> issuePlain(couponId)
        WriteMode.FENCED_NAIVE -> issueFencedNaive(couponId, fence)
        WriteMode.ATOMIC -> issueAtomic(couponId)
        WriteMode.FENCED -> issueFenced(couponId, fence)
    }

    /** 읽고 → 계산하고 → 쓴다. 락이 없으면 lost update 가 난다. */
    private fun issuePlain(couponId: String): Coupon = getOrThrow(couponId).apply { issue() }

    /** 토큰은 보지만 비교와 쓰기가 갈라져 있다. 락이 뚫리면 `last_fence` 도 함께 유실된다. */
    private fun issueFencedNaive(couponId: String, fence: Long): Coupon =
        getOrThrow(couponId).apply { issue(fence) }

    private fun issueAtomic(couponId: String): Coupon {
        if (couponRepository.issueIfAvailable(couponId) == 0) {
            throw IllegalStateException("쿠폰이 모두 소진되었습니다. couponId=$couponId")
        }

        return getOrThrow(couponId)
    }

    /**
     * ⚠️ 영향 행이 0 인 이유가 **둘**이다 — 소진이거나, 토큰이 낡았거나.
     *
     * 다시 조회해서 갈라야 한다. 섞이면 *"락이 뚫려서 막힌 것"* 과 *"정상적으로 소진된 것"* 이
     * 한 덩어리가 돼 측정이 무의미해진다. 지각 쓰기 건수가 곧 **락이 뚫린 횟수**이기 때문이다.
     */
    private fun issueFenced(couponId: String, fence: Long): Coupon {
        if (couponRepository.issueIfAvailable(couponId, fence) > 0) {
            return getOrThrow(couponId)
        }

        // ⚠️ 잠금 읽기여야 한다. 일반 재조회는 REPEATABLE READ 스냅샷이라
        //    남이 방금 올린 last_fence 를 못 보고, 지각 쓰기가 "소진" 으로 잘못 분류된다.
        val coupon = couponRepository.findLatestByCouponId(couponId)
            ?: throw NoSuchElementException("존재하지 않는 쿠폰입니다. couponId=$couponId")

        if (fence <= coupon.lastFence) {
            throw StaleFenceException(couponId, fence, coupon.lastFence)
        }

        throw IllegalStateException("쿠폰이 모두 소진되었습니다. couponId=$couponId")
    }

    @Transactional(readOnly = true)
    fun getRemaining(couponId: String): Int = getOrThrow(couponId).remaining

    @Transactional
    fun register(couponId: String, name: String, totalQuantity: Int): Coupon =
        couponRepository.save(Coupon.of(couponId, name, totalQuantity))

    private fun getOrThrow(couponId: String): Coupon =
        couponRepository.findByCouponId(couponId)
            ?: throw NoSuchElementException("존재하지 않는 쿠폰입니다. couponId=$couponId")
}
