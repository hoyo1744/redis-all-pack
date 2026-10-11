package com.study.redisallpack.infrastructure.metrics

import com.study.redisallpack.domain.layout.ConcurrencyReport
import com.study.redisallpack.domain.layout.LayoutResultRecorder
import com.study.redisallpack.domain.layout.MemoryReport
import com.study.redisallpack.domain.layout.OpsReport
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * "키 N 개 vs 키 하나" 실험의 **마지막 측정값**을 게이지로 내보낸다. 그라파나에서 방식끼리 막대로 비교한다.
 *
 * ```
 * layout_memory_bytes_per_item{layout, items, value_bytes}   아이템당 used_memory 증가분
 * layout_ops_micros{layout, op}                              op = read_one / read_all / update_one
 * layout_concurrency_lost_ratio{layout}                      동시 갱신 유실 비율 (0~1)
 * ```
 *
 * 카운터가 아니라 게이지다. 같은 조건으로 다시 재면 **덮어쓴다** — 누적이 아니라 "지금 결과"다.
 * `items`·`value_bytes` 를 태그로 둬서 경계 실험(512/513 개, 64/65 B)이 서로 덮어쓰지 않게 한다.
 */
@Component
class LayoutMetrics(
    private val registry: MeterRegistry,
) : LayoutResultRecorder {
    private val values = ConcurrentHashMap<Pair<String, Tags>, AtomicReference<Double>>()

    override fun record(report: MemoryReport) {
        set(
            MEMORY, report.bytesPerItem,
            Tags.of("layout", report.layout.name, "items", "${report.items}", "value_bytes", "${report.valueBytes}"),
        )
    }

    override fun record(report: OpsReport) {
        val layout = report.layout.name
        set(OPS, report.readOneMicros, Tags.of("layout", layout, "op", "read_one"))
        set(OPS, report.readAllMicros, Tags.of("layout", layout, "op", "read_all"))
        set(OPS, report.updateOneMicros, Tags.of("layout", layout, "op", "update_one"))
    }

    override fun record(report: ConcurrencyReport) {
        set(LOST, report.lost.toDouble() / report.expected, Tags.of("layout", report.layout.name))
    }

    /** 처음 보는 이름·태그 조합이면 게이지를 등록하고, 이후엔 값만 바꾼다. */
    private fun set(name: String, value: Double, tags: Tags) {
        values.computeIfAbsent(name to tags) { key ->
            AtomicReference(0.0).also { holder ->
                Gauge.builder(key.first, holder) { it.get() }.tags(key.second).register(registry)
            }
        }.set(value)
    }

    private companion object {
        const val MEMORY = "layout.memory.bytes.per.item"
        const val OPS = "layout.ops.micros"
        const val LOST = "layout.concurrency.lost.ratio"
    }
}
