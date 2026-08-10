package com.study.redisallpack.support

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 동시 요청을 밀어넣고 결과를 센다. 락 경합·스탬피드 실험의 공통 하네스. */
object LoadGenerator {

    fun run(threads: Int, iterations: Int, task: (Int) -> Boolean): Result {
        val pool = Executors.newFixedThreadPool(threads)
        val success = AtomicInteger()
        val failure = AtomicInteger()
        val elapsedNanos = AtomicLong()

        val started = System.nanoTime()
        repeat(iterations) { i ->
            pool.submit {
                val t0 = System.nanoTime()
                runCatching { task(i) }
                    .onSuccess { if (it) success.incrementAndGet() else failure.incrementAndGet() }
                    .onFailure { failure.incrementAndGet() }
                elapsedNanos.addAndGet(System.nanoTime() - t0)
            }
        }
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.MINUTES)

        return Result(
            success = success.get(),
            failure = failure.get(),
            totalMillis = (System.nanoTime() - started) / 1_000_000,
            avgMillis = if (iterations == 0) 0.0 else elapsedNanos.get() / 1_000_000.0 / iterations,
        )
    }

    data class Result(val success: Int, val failure: Int, val totalMillis: Long, val avgMillis: Double)
}
