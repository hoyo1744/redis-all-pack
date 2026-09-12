package com.study.redisallpack.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 펜싱 토큰 검사. **Redis 없이 도는 순수 단위 테스트다.**
 *
 * 여기서 재현하려는 건 "락이 뚫린 뒤" 다. 락이 있든 없든 지각 쓰기가 도착할 수 있고,
 * 자원이 그걸 스스로 튕겨내는지가 이 클래스의 관심사다.
 */
class CouponFenceTest {

    @Nested
    @DisplayName("펜싱 토큰으로 발급할 때")
    inner class Fenced {

        @Test
        fun `토큰이 커지는 순서면 계속 발급된다`() {
            val coupon = Coupon.of("BF", "블프 쿠폰", totalQuantity = 10)

            coupon.issue(1)
            coupon.issue(2)
            coupon.issue(3)

            assertThat(coupon.issuedQuantity).isEqualTo(3)
            assertThat(coupon.lastFence).isEqualTo(3)
        }

        @Test
        fun `이미 지난 토큰은 거부한다 - GC STW 뒤 깨어난 지각 쓰기`() {
            val coupon = Coupon.of("BF", "블프 쿠폰", totalQuantity = 10)

            // A 가 토큰 5 를 받고 멈춘 사이, B 가 토큰 6 으로 먼저 쓴다.
            coupon.issue(6)

            assertThatThrownBy { coupon.issue(5) }
                .isInstanceOf(StaleFenceException::class.java)
                .hasMessageContaining("토큰=5")
                .hasMessageContaining("마지막=6")

            // 거부됐으므로 수량이 늘지 않아야 한다. 늘었다면 초과 발급이다.
            assertThat(coupon.issuedQuantity).isEqualTo(1)
        }

        @Test
        fun `같은 토큰도 거부한다 - 재시도로 같은 요청이 두 번 오는 경우`() {
            val coupon = Coupon.of("BF", "블프 쿠폰", totalQuantity = 10)

            coupon.issue(7)

            assertThatThrownBy { coupon.issue(7) }
                .isInstanceOf(StaleFenceException::class.java)

            assertThat(coupon.issuedQuantity).isEqualTo(1)
        }

        @Test
        fun `토큰이 유효해도 소진됐으면 발급하지 않는다`() {
            val coupon = Coupon.of("BF", "블프 쿠폰", totalQuantity = 1)

            coupon.issue(1)

            assertThatThrownBy { coupon.issue(2) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("소진")
        }

        @Test
        fun `토큰 검사가 수량 검사보다 먼저다 - 지각 쓰기는 소진 여부와 무관하게 튕긴다`() {
            val coupon = Coupon.of("BF", "블프 쿠폰", totalQuantity = 1)

            coupon.issue(10)

            // 소진(remaining=0) 이면서 토큰도 낡았다. 둘 중 토큰이 먼저 걸려야
            // 지표에서 "지각 쓰기" 와 "소진" 이 안 섞인다.
            assertThatThrownBy { coupon.issue(9) }
                .isInstanceOf(StaleFenceException::class.java)
        }
    }

    @Nested
    @DisplayName("펜싱 없이 발급할 때")
    inner class Unfenced {

        @Test
        fun `수량만 보고 발급한다 - lastFence 는 그대로다`() {
            val coupon = Coupon.of("BF", "블프 쿠폰", totalQuantity = 10)

            coupon.issue()
            coupon.issue()

            assertThat(coupon.issuedQuantity).isEqualTo(2)
            assertThat(coupon.lastFence).isEqualTo(0)
        }

        @Test
        fun `소진되면 막는다`() {
            val coupon = Coupon.of("BF", "블프 쿠폰", totalQuantity = 1)

            coupon.issue()

            assertThatThrownBy { coupon.issue() }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }
}
