package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.FenceGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * `INCR` 로 번호를 뽑는다. 원자적이라 재시도가 없다.
 *
 * ⚠️ **failover 에 약하다.** 비동기 복제라 카운터가 되감길 수 있고, 되감기면 펜싱이 무의미해진다.
 * `coupon.fence.source=rdb|zookeeper` 로 갈아끼울 수 있다.
 */
@Component
@Qualifier(FENCE_DELEGATE)
@ConditionalOnProperty(name = ["coupon.fence.source"], havingValue = "redis", matchIfMissing = true)
class RedisFenceGenerator(
    private val redisTemplate: StringRedisTemplate,
) : FenceGenerator {

    override val source = "redis-incr"

    override fun next(key: String): Long =
        redisTemplate.opsForValue().increment("$FENCE_PREFIX$key")
            ?: throw IllegalStateException("펜싱 토큰을 발급하지 못했습니다. key=$key")

    companion object {
        private const val FENCE_PREFIX = "fence:"
    }
}
