package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.DistributedLock
import com.study.redisallpack.domain.lock.LockType
import org.springframework.stereotype.Component

/**
 * 구현 넷을 타입으로 골라 쓴다. 동시에 다 띄워야 JIT 예열·커넥션 풀 상태가 같아 비교가 성립한다.
 */
@Component
class DistributedLocks(
    locks: List<DistributedLock>,
) {
    private val byType: Map<LockType, DistributedLock> = locks.associateBy { it.type }

    val available: Set<LockType> get() = byType.keys

    fun of(type: LockType): DistributedLock =
        byType[type] ?: throw IllegalArgumentException(
            "사용할 수 없는 락입니다. type=$type 가능=$available",
        )
}
