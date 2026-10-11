package com.study.redisallpack.interfaces.api.layout

import com.study.redisallpack.application.layout.LayoutFacade
import com.study.redisallpack.domain.layout.ItemLayout
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * "키 N 개 vs 키 하나" 실험 지점. 전부 실험용이라 데이터를 넣었다가 지운다.
 *
 * ```
 * POST /api/v1/layout/memory/compare?groups=1000&items=100&valueBytes=10
 * POST /api/v1/layout/memory/showcase                        ← 그라파나 시간축 계단용 (약 2 분)
 * POST /api/v1/layout/ops/compare?items=100&valueBytes=10&iterations=2000
 * POST /api/v1/layout/concurrency/compare?threads=20&perThread=200
 * ```
 */
@RestController
@RequestMapping("/api/v1/layout")
class LayoutV1Controller(
    private val layoutFacade: LayoutFacade,
) {

    @PostMapping("/memory")
    fun memory(
        @RequestParam layout: ItemLayout,
        @RequestParam(defaultValue = "1000") groups: Int,
        @RequestParam(defaultValue = "100") items: Int,
        @RequestParam(defaultValue = "10") valueBytes: Int,
        /** true 면 지우지 않고 남긴다. redis-cli 로 직접 들여다볼 때. */
        @RequestParam(defaultValue = "false") keep: Boolean,
    ) = layoutFacade.measureMemory(layout, groups, items, valueBytes, keep)

    @PostMapping("/memory/compare")
    fun memoryCompare(
        @RequestParam(defaultValue = "1000") groups: Int,
        @RequestParam(defaultValue = "100") items: Int,
        @RequestParam(defaultValue = "10") valueBytes: Int,
    ) = layoutFacade.compareMemory(groups, items, valueBytes)

    /**
     * 그라파나 시간축 시연. 방식마다 넣고 → 유지 → 지우기를 차례로 한다.
     * 기본값이면 (20 + 10) 초 × 4 방식 ≈ 2 분 동안 응답이 안 온다.
     */
    @PostMapping("/memory/showcase")
    fun memoryShowcase(
        @RequestParam(defaultValue = "1000") groups: Int,
        @RequestParam(defaultValue = "100") items: Int,
        @RequestParam(defaultValue = "10") valueBytes: Int,
        @RequestParam(defaultValue = "20") holdSeconds: Long,
        @RequestParam(defaultValue = "10") gapSeconds: Long,
    ) = layoutFacade.showcaseMemory(groups, items, valueBytes, holdSeconds, gapSeconds)

    @PostMapping("/ops/compare")
    fun opsCompare(
        @RequestParam(defaultValue = "100") items: Int,
        @RequestParam(defaultValue = "10") valueBytes: Int,
        @RequestParam(defaultValue = "2000") iterations: Int,
    ) = layoutFacade.compareOps(items, valueBytes, iterations)

    @PostMapping("/concurrency/compare")
    fun concurrencyCompare(
        @RequestParam(defaultValue = "20") threads: Int,
        @RequestParam(defaultValue = "200") perThread: Int,
    ) = layoutFacade.compareConcurrency(threads, perThread)
}
