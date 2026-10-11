package com.study.redisallpack.infrastructure.layout

import com.study.redisallpack.domain.layout.MemoryProbe
import com.study.redisallpack.support.ClusterInspector
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RedisMemoryProbe(
    private val redisTemplate: StringRedisTemplate,
    private val clusterInspector: ClusterInspector,
) : MemoryProbe {

    /**
     * 키 하나의 `MEMORY USAGE` 는 그 키가 "대략" 얼마인지만 알려준다. 해시 테이블 버킷, 만료 사전,
     * 할당기 반올림 같은 건 빠진다. 그래서 **서버 전체 used_memory 의 증가분**을 주 지표로 쓴다.
     */
    override fun usedMemory(): Long =
        redisTemplate.connectionFactory!!.clusterConnection.use { conn ->
            conn.clusterGetNodes()
                .filter { it.isMaster }
                .sumOf { node ->
                    conn.serverCommands().info(node, "memory")
                        ?.getProperty("used_memory")?.toLong() ?: 0L
                }
        }

    override fun encodingOf(key: String): String =
        redisTemplate.execute { it.keyCommands().encodingOf(key.toByteArray()) }?.raw() ?: "none"

    /**
     * `MEMORY USAGE` 는 스프링에 전용 API 가 없다. 범용 `execute` 는 응답을 바이트로만 받아서
     * 정수 응답에 터진다. Lettuce 클러스터 API 로 내려가면 키로 노드를 찾아 Long 으로 받는다.
     */
    @Suppress("UNCHECKED_CAST")
    override fun memoryUsageOf(key: String): Long? =
        redisTemplate.connectionFactory!!.clusterConnection.use { conn ->
            (conn.nativeConnection as RedisAdvancedClusterAsyncCommands<ByteArray, ByteArray>)
                .memoryUsage(key.toByteArray())
                .get(2, TimeUnit.SECONDS)
        }

    override fun nodeDistribution(keys: List<String>): Map<String, Int> =
        clusterInspector.distribution(keys)
}
