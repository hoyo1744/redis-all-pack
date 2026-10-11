package com.study.redisallpack.application.layout

import com.study.redisallpack.domain.layout.ConcurrencyReport
import com.study.redisallpack.domain.layout.ItemLayout
import com.study.redisallpack.domain.layout.ItemStore
import com.study.redisallpack.domain.layout.LayoutResultRecorder
import com.study.redisallpack.domain.layout.MemoryProbe
import com.study.redisallpack.domain.layout.MemoryReport
import com.study.redisallpack.domain.layout.OpsReport
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * "키 N 개로 쪼개기 vs 키 하나에 몰아넣기" 실험.
 *
 * 세 저장 모양([ItemLayout])에 **같은 데이터, 같은 연산**을 넣고 메모리·지연·동시성을 비교한다.
 */
@Component
class LayoutFacade(
    stores: List<ItemStore>,
    private val probe: MemoryProbe,
    private val recorder: LayoutResultRecorder,
) {

    private val stores = stores.associateBy { it.layout }

    /**
     * 그룹 [groups] 개 × 아이템 [items] 개를 넣고 `used_memory` 증가분을 잰다.
     *
     * 그룹을 여러 개 넣는 이유: 키 하나의 차이는 수십 바이트라 할당기 반올림에 묻힌다.
     * 수만~수십만 키를 쌓아야 키당 오버헤드가 평균으로 드러난다.
     */
    fun measureMemory(layout: ItemLayout, groups: Int, items: Int, valueBytes: Int, keep: Boolean): MemoryReport {
        val store = store(layout)
        val ids = itemIds(items)
        val groupIds = (1..groups).map { "m$it" }

        groupIds.forEach { store.delete(it, ids) }   // 이전 실행 잔여물 정리
        val before = probe.usedMemory()

        groupIds.forEach { store.writeAll(it, ids.associateWith { randomValue(valueBytes) }) }
        val after = probe.usedMemory()

        val firstKeys = store.keysOf(groupIds.first(), ids)
        val report = MemoryReport(
            layout = layout,
            groups = groups,
            items = items,
            valueBytes = valueBytes,
            keys = groups * firstKeys.size,
            usedBytes = after - before,
            bytesPerItem = (after - before).toDouble() / (groups.toLong() * items),
            encoding = probe.encodingOf(firstKeys.first()),
            memoryUsagePerGroup = firstKeys.mapNotNull { probe.memoryUsageOf(it) }.sum(),
        )
        log.info("[메모리] {}", report)
        recorder.record(report)

        if (!keep) groupIds.forEach { store.delete(it, ids) }
        return report
    }

    fun compareMemory(groups: Int, items: Int, valueBytes: Int) =
        ItemLayout.entries.map { measureMemory(it, groups, items, valueBytes, keep = false) }

    /**
     * 그라파나 시간축용 시연. 방식마다 **넣고 → [holdSeconds] 유지 → 지우고 → [gapSeconds] 쉰다.**
     *
     * 측정 API 는 재자마자 지워서 5 초 간격 스크랩에 안 잡힌다. 여기선 데이터를 잠시 남겨
     * 노드 메모리·키 수가 방식마다 **계단처럼 오르내리는** 모습을 보이게 한다.
     */
    fun showcaseMemory(groups: Int, items: Int, valueBytes: Int, holdSeconds: Long, gapSeconds: Long) =
        ItemLayout.entries.map { layout ->
            val report = measureMemory(layout, groups, items, valueBytes, keep = true)
            TimeUnit.SECONDS.sleep(holdSeconds)
            clear(layout, groups, items)
            TimeUnit.SECONDS.sleep(gapSeconds)
            report
        }

    fun clear(layout: ItemLayout, groups: Int, items: Int) {
        val ids = itemIds(items)
        (1..groups).forEach { store(layout).delete("m$it", ids) }
    }

    /**
     * 그룹 하나에 대해 "하나 읽기 / 전부 읽기 / 하나 쓰기" 를 [iterations] 번씩 돌려 평균을 낸다.
     * 앞의 200 번은 JIT·커넥션 예열로 버린다.
     */
    fun measureOps(layout: ItemLayout, items: Int, valueBytes: Int, iterations: Int): OpsReport {
        val store = store(layout)
        val ids = itemIds(items)
        val group = "ops"

        store.delete(group, ids)
        store.writeAll(group, ids.associateWith { randomValue(valueBytes) })

        fun randomId() = ids[ThreadLocalRandom.current().nextInt(ids.size)]
        val readOne = averageMicros(iterations) { store.readOne(group, randomId()) }
        val readAll = averageMicros(iterations) { store.readAll(group, ids) }
        val updateOne = averageMicros(iterations) { store.updateOne(group, randomId(), randomValue(valueBytes)) }

        val report = OpsReport(
            layout = layout,
            items = items,
            valueBytes = valueBytes,
            iterations = iterations,
            readOneMicros = readOne,
            readAllMicros = readAll,
            updateOneMicros = updateOne,
            nodes = probe.nodeDistribution(store.keysOf(group, ids)),
        )
        log.info("[연산] {}", report)
        recorder.record(report)

        store.delete(group, ids)
        return report
    }

    fun compareOps(items: Int, valueBytes: Int, iterations: Int) =
        ItemLayout.entries.map { measureOps(it, items, valueBytes, iterations) }

    /**
     * 스레드마다 **서로 다른 아이템**을 맡아 [perThread] 번씩 1 을 더한다.
     *
     * 아이템이 다르니 원래는 부딪힐 이유가 없다. 그런데 JSON 통째 저장은 키가 하나라
     * 남이 고친 아이템까지 내 SET 이 덮어쓴다. 기대값과 실제 합의 차이가 유실된 갱신 수다.
     */
    fun measureConcurrency(layout: ItemLayout, threads: Int, perThread: Int): ConcurrencyReport {
        val store = store(layout)
        val ids = itemIds(threads)                     // 스레드 하나에 아이템 하나
        val group = "conc"

        store.delete(group, ids)
        store.writeAll(group, ids.associateWith { "0" })

        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        ids.forEach { id ->
            pool.submit {
                start.await()
                try {
                    repeat(perThread) { store.increment(group, id) }
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        done.await(60, TimeUnit.SECONDS)
        pool.shutdown()

        val actual = store.readAll(group, ids).values.sumOf { it.toLong() }
        val expected = threads.toLong() * perThread
        val report = ConcurrencyReport(layout, threads, perThread, expected, actual, expected - actual)
        log.info("[동시성] {}", report)
        recorder.record(report)

        store.delete(group, ids)
        return report
    }

    fun compareConcurrency(threads: Int, perThread: Int) =
        ItemLayout.entries.map { measureConcurrency(it, threads, perThread) }

    private fun store(layout: ItemLayout) = stores.getValue(layout)

    private fun itemIds(items: Int) = (1..items).map { "i$it" }

    private inline fun averageMicros(iterations: Int, op: () -> Unit): Double {
        repeat(WARMUP) { op() }
        val started = System.nanoTime()
        repeat(iterations) { op() }
        return (System.nanoTime() - started) / 1_000.0 / iterations
    }

    /**
     * 영문자만 쓴다. 숫자로만 된 값은 Redis 가 정수(`int`) 인코딩으로 바꿔버리고,
     * 0~9999 는 아예 **공유 객체**를 써서 값 메모리가 0 이 된다. 비교가 오염된다.
     */
    private fun randomValue(bytes: Int): String {
        val r = ThreadLocalRandom.current()
        return String(CharArray(bytes) { ('a' + r.nextInt(26)) })
    }

    private companion object {
        const val WARMUP = 200
        val log = LoggerFactory.getLogger(LayoutFacade::class.java)
    }
}
