package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.FenceGenerator
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * 토큰 발급 비용만 따로 잰다. 전체 응답 시간에 묻히므로 이 구간을 떼어내야 구현 차이가 보인다.
 */
@Component
@Primary
class TimedFenceGenerator(
    @Qualifier(FENCE_DELEGATE) private val delegate: FenceGenerator,
    registry: MeterRegistry,
) : FenceGenerator {

    override val source = delegate.source

    private val timer = Timer.builder("coupon.fence.issue")
        .tag("source", delegate.source)
        .register(registry)

    override fun next(key: String): Long = timer.recordCallable { delegate.next(key) }!!
}

/** 이게 없으면 데코레이터가 자기 자신을 주입받는다. */
const val FENCE_DELEGATE = "fenceDelegate"
