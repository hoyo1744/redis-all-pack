package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.DistributedLock
import com.study.redisallpack.domain.lock.Fence
import com.study.redisallpack.domain.lock.FenceGenerator
import com.study.redisallpack.domain.lock.LockAcquisitionException
import com.study.redisallpack.domain.lock.LockType
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Redisson `RLock`. 겉보기는 스핀락과 같은 일을 하지만 **기다리는 방식이 다르다.**
 *
 * 락을 못 잡으면 해당 락 채널을 구독하고 눕는다. 앞사람이 풀면서 메시지를 쏘면 그때 깨어난다.
 * 대기 중에는 Redis 를 치지 않는다 — [SpinLock] 이 5ms 마다 왕복하는 그 트래픽이 통째로 없다.
 * 경합이 심할수록 이 차이가 벌어지는 게 1주차 측정의 핵심이다.
 *
 * 내부 구현은 문자열이 아니라 **Hash + 재진입 카운트**다. 그래서 같은 스레드가 다시 잡으면
 * 카운트만 오르고, `unlock` 을 그 횟수만큼 해야 실제로 풀린다.
 *
 * ⚠️ **watchdog 을 쓰지 않는다.** `leaseTime` 을 주면 Redisson 은 자동 연장을 끈다.
 * 연장을 켜면 (`leaseTime = -1`) 작업이 끝날 때까지 락이 안 풀려 TTL 초과 상황 자체가 안 생기고,
 * 그러면 **펜싱이 왜 필요한지를 재현할 수 없다.** 일부러 만료되게 둔다.
 */
@Component
class RedissonLock(
    private val redissonClient: RedissonClient,
    private val fenceGenerator: FenceGenerator,
) : DistributedLock {

    private val log = LoggerFactory.getLogger(javaClass)

    override val type = LockType.REDISSON

    override fun <T> withLock(key: String, wait: Duration, lease: Duration, block: (Fence) -> T): T {
        val lock = redissonClient.getLock(key)

        val acquired = lock.tryLock(wait.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS)

        if (!acquired) {
            throw LockAcquisitionException(key, type)
        }

        log.debug("[Redisson] 획득 key={}", key)

        return try {
            block(Fence(fenceGenerator.next(key), lease))
        } finally {
            release(lock, key)
        }
    }

    /**
     * ⚠️ **`isHeldByCurrentThread` 확인 없이 풀면 안 된다.**
     * TTL 이 먼저 끝났으면 락은 이미 남의 것이고, 그 상태의 `unlock` 은
     * `IllegalMonitorStateException` 을 던져 **정상 응답까지 500 으로 바꾼다.**
     * 이미 놓친 락은 로그만 남기고 넘어간다 — 그 사실을 아는 게 중요하지, 여기서 터뜨릴 일은 아니다.
     */
    private fun release(lock: org.redisson.api.RLock, key: String) {
        if (lock.isHeldByCurrentThread) {
            lock.unlock()
            return
        }

        log.warn("[Redisson] 해제 실패 — 이미 내 락이 아닙니다. key={} (TTL 초과 의심)", key)
    }
}
