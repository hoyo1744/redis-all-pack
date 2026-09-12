package com.study.redisallpack.infrastructure.coupon

import com.study.redisallpack.domain.coupon.CouponCacheRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CouponCacheRepositoryImpl(
    private val redisTemplate: StringRedisTemplate,
) : CouponCacheRepository {

    override fun getRemaining(couponId: String): Int? =
        redisTemplate.opsForValue().get(key(couponId))?.toIntOrNull()

    override fun putRemaining(couponId: String, remaining: Int, ttl: Duration) {
        redisTemplate.opsForValue().set(key(couponId), remaining.toString(), ttl)
    }

    override fun evict(couponId: String) {
        redisTemplate.delete(key(couponId))
    }

    /**
     * 키가 여러 슬롯에 걸리면 클러스터에서 MGET 이 CROSSSLOT 으로 실패한다.
     * 4주차에서 슬롯별 분할과 해시태그를 비교하는 출발점이다.
     */
    override fun getRemainings(couponIds: List<String>): Map<String, Int> {
        val keys = couponIds.map { key(it) }
        val values = redisTemplate.opsForValue().multiGet(keys).orEmpty()

        return couponIds.zip(values)
            .mapNotNull { (id, raw) -> raw?.toIntOrNull()?.let { id to it } }
            .toMap()
    }

    /** 해시태그 없이 둔다. 4주차에서 `{...}` 를 씌웠을 때 분포가 어떻게 바뀌는지 비교한다. */
    private fun key(couponId: String) = "event:remaining:$couponId"
}
