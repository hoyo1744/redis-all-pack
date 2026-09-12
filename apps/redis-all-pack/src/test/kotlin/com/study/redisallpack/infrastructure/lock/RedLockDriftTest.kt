package com.study.redisallpack.infrastructure.lock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * 시계 오차 보정 계산. **Redis 없이 도는 순수 단위 테스트다.**
 *
 * RedLock 이 다른 락과 갈리는 지점이 여기다 —
 * *"락을 얻었다"* 와 *"얻은 락을 믿어도 된다"* 를 분리해서 계산한다.
 */
class RedLockDriftTest {

    @Test
    @DisplayName("보정값은 lease 의 1% + 2ms 다")
    fun `드리프트 계산`() {
        assertThat(RedLock.drift(Duration.ofSeconds(10))).isEqualTo(Duration.ofMillis(102))
        assertThat(RedLock.drift(Duration.ofSeconds(3))).isEqualTo(Duration.ofMillis(32))
        assertThat(RedLock.drift(Duration.ofMillis(100))).isEqualTo(Duration.ofMillis(3))
    }

    @Test
    @DisplayName("유효 시간은 lease 에서 획득 경과와 보정값을 뺀 값이다")
    fun `유효 시간 계산`() {
        // lease 10s, 획득에 200ms, 보정 102ms → 9698ms
        val validity = RedLock.validity(Duration.ofSeconds(10), Duration.ofMillis(200))

        assertThat(validity).isEqualTo(Duration.ofMillis(9698))
    }

    @Test
    @DisplayName("획득에 lease 를 거의 다 쓰면 유효 시간이 음수가 된다")
    fun `느린 획득은 락이 아니다`() {
        // 죽은 노드 하나에서 타임아웃을 다 기다린 상황.
        // 과반을 채웠더라도 이 락으로 일을 시작하면 안 된다 — 이미 남이 가져갈 시각이다.
        val validity = RedLock.validity(Duration.ofSeconds(3), Duration.ofMillis(2990))

        assertThat(validity.isNegative).isTrue()
    }

    @Test
    @DisplayName("경계 - 보정값이 없었다면 통과했을 구간을 보정값이 잘라낸다")
    fun `보정값이 하는 일`() {
        val lease = Duration.ofSeconds(3)
        val elapsed = Duration.ofMillis(2980)

        // 보정 없이 단순 뺄셈이면 20ms 가 남아 "락을 얻었다" 고 판단한다.
        assertThat(lease - elapsed).isEqualTo(Duration.ofMillis(20))

        // 보정(32ms)을 빼면 음수다. 노드 시계가 조금만 빨라도 이미 만료됐을 구간이라 버린다.
        assertThat(RedLock.validity(lease, elapsed).isNegative).isTrue()
    }
}
