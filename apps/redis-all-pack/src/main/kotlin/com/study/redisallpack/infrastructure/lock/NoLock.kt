package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.DistributedLock
import com.study.redisallpack.domain.lock.Fence
import com.study.redisallpack.domain.lock.FenceGenerator
import com.study.redisallpack.domain.lock.LockType
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 락 없이 실행하는 기준선. 이 숫자가 없으면 뒤에 붙이는 락들이 무엇을 막았는지 말할 수 없다.
 */
@Component
class NoLock(
    private val fenceGenerator: FenceGenerator,
) : DistributedLock {

    override val type = LockType.NONE

    override fun <T> withLock(key: String, wait: Duration, lease: Duration, block: (Fence) -> T): T =
        block(Fence(fenceGenerator.next(key), lease))
}
