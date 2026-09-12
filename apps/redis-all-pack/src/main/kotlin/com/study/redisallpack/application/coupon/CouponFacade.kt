package com.study.redisallpack.application.coupon

import com.study.redisallpack.domain.coupon.CouponCacheRepository
import com.study.redisallpack.domain.coupon.CouponService
import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.infrastructure.hotkey.RemainingReaders
import com.study.redisallpack.domain.lock.FenceGenerator
import com.study.redisallpack.domain.lock.LockType
import com.study.config.redis.RedLockNodes
import com.study.redisallpack.infrastructure.lock.DistributedLocks
import com.study.redisallpack.infrastructure.metrics.CouponMetrics
import com.study.redisallpack.infrastructure.metrics.CouponStateMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * 선착순 쿠폰 발급. 락과 쓰기 방식을 요청마다 받아 같은 프로세스에서 갈아끼운다.
 */
@Component
class CouponFacade(
    private val couponService: CouponService,
    private val couponCacheRepository: CouponCacheRepository,
    private val distributedLocks: DistributedLocks,
    /** 토큰 출처. 측정 결과 해석에 필요해서 [settings] 로 노출한다. */
    private val fenceGenerator: FenceGenerator,
    private val couponMetrics: CouponMetrics,
    private val couponStateMetrics: CouponStateMetrics,
    private val remainingReaders: RemainingReaders,
    /** 클럭 드리프트 실험이 켜져 있는지 [settings] 로 드러낸다. 모르면 측정값을 오해한다. */
    private val redLockNodes: RedLockNodes? = null,
    /** 짧게 주면 임계 구역이 TTL 을 넘겨 **락이 뚫리는 상황**을 만들 수 있다. 3주차 재현용. */
    @Value("\${coupon.lock.lease-millis:3000}") leaseMillis: Long,
    @Value("\${coupon.lock.wait-millis:500}") waitMillis: Long,
) {
    private val lease: Duration = Duration.ofMillis(leaseMillis)
    private val wait: Duration = Duration.ofMillis(waitMillis)

    /** ⚠️ 락이 트랜잭션보다 바깥이어야 한다. 뒤집히면 커밋 전에 락이 풀려 초과 발급이 난다. */
    fun issue(criteria: CouponCriteria.Issue): CouponResult.Issued =
        couponMetrics.recordIssue(criteria.lockType, criteria.writeMode, criteria.couponId) { doIssue(criteria) }

    private fun doIssue(criteria: CouponCriteria.Issue): CouponResult.Issued {
        val lock = distributedLocks.of(criteria.lockType)

        return lock.withLock(lockKey(criteria.couponId), wait, lease) { fence ->
            if (criteria.pauseMillis > 0) {
                Thread.sleep(ThreadLocalRandom.current().nextLong(criteria.pauseMillis + 1))
            }

            val coupon = couponService.issue(criteria.couponId, criteria.writeMode, fence.token)

            // HTTP 200 수와 이 값의 차이가 곧 허수다.
            couponStateMetrics.record(coupon)

            // 6주차에서 이 무효화가 스탬피드의 방아쇠가 된다.
            couponCacheRepository.evict(criteria.couponId)

            CouponResult.Issued.of(coupon, criteria.userId, criteria.lockType, criteria.writeMode, fence.token)
        }
    }

    /**
     * 캐시 미스면 DB 로 간다.
     *
     * 조회는 발급보다 훨씬 많고 **키가 하나라 한 슬롯에 몰린다.** 2주차에서 [HotKeyStrategy] 를
     * 갈아끼우며 그 쏠림을 어떻게 푸는지 본다.
     */
    fun getRemaining(couponId: String, strategy: HotKeyStrategy = HotKeyStrategy.NONE): CouponResult.Remaining {
        val reader = remainingReaders.of(strategy)

        reader.read(couponId)?.let {
            return CouponResult.Remaining(couponId, it, fromCache = true)
        }

        val remaining = couponService.getRemaining(couponId)
        reader.write(couponId, remaining)

        return CouponResult.Remaining(couponId, remaining, fromCache = false)
    }

    fun availableHotKeyStrategies(): Set<HotKeyStrategy> = remainingReaders.available

    /** 4주차 MGET 비교용. */
    fun getRemainings(couponIds: List<String>): Map<String, Int> =
        couponCacheRepository.getRemainings(couponIds)

    fun register(criteria: CouponCriteria.Register) =
        couponService.register(criteria.couponId, criteria.name, criteria.totalQuantity)
            .let { CouponResult.Remaining(it.couponId, it.remaining, fromCache = false) }

    /** 어떤 락을 쓸 수 있는지. RedLock 노드를 안 띄웠으면 목록에서 빠진다. */
    fun availableLocks(): Set<LockType> = distributedLocks.available

    /** 지금 설정. 측정 결과를 해석하려면 lease 를 알아야 한다. */
    fun settings(): Map<String, Any> = mapOf(
        "leaseMillis" to lease.toMillis(),
        "waitMillis" to wait.toMillis(),
        "locks" to availableLocks(),
        "fenceSource" to fenceGenerator.source,
        "hotKeyStrategies" to availableHotKeyStrategies(),
        "clockSkewed" to (redLockNodes?.skewed ?: false),
        "nodeSkewMillis" to (redLockNodes?.let { n -> (0 until n.size).map { n.skewOf(it) } } ?: emptyList<Long>()),
    )

    /** `{coupon:ID}` 해시태그로 같은 쿠폰의 락을 한 슬롯에 묶는다. 2주차에서 이게 핫스팟이 된다. */
    private fun lockKey(couponId: String) = "lock:{coupon:$couponId}"

    companion object {
        private val CACHE_TTL: Duration = Duration.ofSeconds(30)
    }
}
