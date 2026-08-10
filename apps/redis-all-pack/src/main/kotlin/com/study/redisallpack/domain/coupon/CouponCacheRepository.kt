package com.study.redisallpack.domain.coupon

import java.time.Duration

/**
 * 도메인은 인터페이스만 알고 구현은 infrastructure 가 한다.
 * 5·6주차에서 구현만 갈아끼워 지터 · softTTL · PER 을 비교한다.
 */
interface CouponCacheRepository {

    fun getRemaining(couponId: String): Int?

    fun putRemaining(couponId: String, remaining: Int, ttl: Duration)

    fun evict(couponId: String)

    /** 4주차 MGET 비교용. 여러 쿠폰의 잔여 수량을 한 번에 가져온다. */
    fun getRemainings(couponIds: List<String>): Map<String, Int>
}
