package com.study.redisallpack.infrastructure.lock

import com.study.config.redis.RedLockNodes
import com.study.redisallpack.domain.lock.DistributedLock
import com.study.redisallpack.domain.lock.Fence
import com.study.redisallpack.domain.lock.FenceGenerator
import com.study.redisallpack.domain.lock.LockAcquisitionException
import com.study.redisallpack.domain.lock.LockType
import io.lettuce.core.ScriptOutputType
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@Component
@ConditionalOnProperty(name = ["redis.redlock.enabled"], havingValue = "true", matchIfMissing = true)
class RedLock(
    private val nodes: RedLockNodes,
    private val fenceGenerator: FenceGenerator,
) : DistributedLock {

    private val log = LoggerFactory.getLogger(javaClass)

    override val type = LockType.REDLOCK

    override fun <T> withLock(key: String, wait: Duration, lease: Duration, block: (Fence) -> T): T {
        require(nodes.size > 0) { "RedLock 노드가 없습니다. redis.redlock.nodes 를 설정하세요." }

        val token = UUID.randomUUID().toString()
        val deadline = System.nanoTime() + wait.toNanos()

        while (true) {
            val validity = tryAcquireQuorum(key, token, lease)

            if (validity != null) {
                log.debug("[RedLock] 획득 key={} 유효={}ms", key, validity.toMillis())

                return try {
                    block(Fence(fenceGenerator.next(key), validity))
                } finally {
                    releaseAll(key, token)
                }
            }

            if (System.nanoTime() >= deadline) {
                throw LockAcquisitionException(key, type, "과반을 얻지 못했습니다. key=$key 노드=${nodes.size}")
            }

            Thread.sleep(ThreadLocalRandom.current().nextLong(RETRY_JITTER_MILLIS))
        }
    }

    private fun tryAcquireQuorum(key: String, token: String, lease: Duration): Duration? {
        val startedAt = System.nanoTime()

        val futures = nodes.connections.mapIndexed { i, conn ->
            val px = (lease.toMillis() - nodes.skewOf(i)).coerceAtLeast(1)
            conn.async().set(key, token, SetArgs.Builder.nx().px(px))
        }

        val acquired = futures.count { runCatching { it.get() }.getOrNull() == OK }

        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)
        val validity = validity(lease, elapsed)

        if (acquired >= nodes.quorum && !validity.isNegative && !validity.isZero) {
            return validity
        }

        log.debug(
            "[RedLock] 실패 key={} 획득={}/{} 과반={} 경과={}ms 유효={}ms",
            key, acquired, nodes.size, nodes.quorum, elapsed.toMillis(), validity.toMillis(),
        )

        releaseAll(key, token)

        return null
    }

    private fun releaseAll(key: String, token: String) {
        nodes.connections.forEach { conn ->
            runCatching {
                conn.async().eval<Long>(UNLOCK_SCRIPT, ScriptOutputType.INTEGER, arrayOf(key), token)
            }.onFailure { log.debug("[RedLock] 해제 실패 key={} {}", key, it.message) }
        }
    }

    companion object {
        private const val OK = "OK"

        const val CLOCK_DRIFT_FACTOR = 0.01
        const val CLOCK_DRIFT_ALLOWANCE_MILLIS = 2L

        fun drift(lease: Duration): Duration =
            Duration.ofMillis((lease.toMillis() * CLOCK_DRIFT_FACTOR).toLong() + CLOCK_DRIFT_ALLOWANCE_MILLIS)

        fun validity(lease: Duration, elapsed: Duration): Duration = lease - elapsed - drift(lease)

        private const val RETRY_JITTER_MILLIS = 20L

        private val UNLOCK_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
        """.trimIndent()
    }
}
