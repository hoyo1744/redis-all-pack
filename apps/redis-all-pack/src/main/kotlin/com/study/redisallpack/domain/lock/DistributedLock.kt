package com.study.redisallpack.domain.lock

import java.time.Duration

/** 도메인은 이 인터페이스만 안다. Lettuce · Redisson · RedLock 은 infrastructure 의 사정이다. */
interface DistributedLock {

    val type: LockType

    /**
     * 락을 잡고 [block] 을 실행한다. 못 잡으면 [LockAcquisitionException].
     *
     * @param lease 락의 수명(TTL). **이 시간이 지나면 내가 아직 일하는 중이어도 남이 가져간다.**
     */
    fun <T> withLock(key: String, wait: Duration, lease: Duration, block: (Fence) -> T): T
}

/**
 * 펜싱 토큰. 락이 만료된 걸 모르는 클라이언트를 막지 못하므로,
 * 락 획득 순서대로 번호를 받아 쓰기에 싣고 **자원이 낡은 번호를 거부**하게 한다.
 *
 * @param validity 이 토큰으로 안전하다고 주장할 수 있는 남은 시간.
 *   RedLock 은 획득 경과와 시계 오차를 빼고 계산한다.
 */
data class Fence(
    val token: Long,
    val validity: Duration,
) {
    fun isExpired(): Boolean = validity.isZero || validity.isNegative
}

/** 대기 시간 안에 락을 못 잡았다. 선착순에서는 정상 경로다. */
class LockAcquisitionException(
    val key: String,
    val type: LockType,
    message: String = "락을 획득하지 못했습니다. key=$key type=$type",
) : RuntimeException(message)
