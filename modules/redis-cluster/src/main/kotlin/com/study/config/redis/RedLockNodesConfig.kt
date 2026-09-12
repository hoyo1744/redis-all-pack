package com.study.config.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * RedLock 노드 목록. **클러스터와 다른 인스턴스다** — 클러스터에서는 키가 슬롯 하나에 고정돼
 * 세 노드에 같은 락 키를 심을 수 없다. `docker/redis-redlock-compose.yml` 참고.
 *
 * ⚠️ [nodes] 에 기본값을 주면 안 된다. 전부 기본값이면 코틀린이 무인자 생성자를 만들어
 * `@ConstructorBinding` 이 기동을 거부한다.
 */
@ConfigurationProperties(prefix = "redis.redlock")
data class RedLockProperties @ConstructorBinding constructor(
    val nodes: List<String>,
    /** 노드 하나에 거는 시간 상한. 죽은 노드에서 오래 붙들리면 획득 전체가 늦어진다. */
    val timeoutMillis: Long = 300,
    /**
     * 노드별 시계 앞섬(ms). 실험용이며 기본은 0.
     *
     * 시계를 돌리는 대신 그 노드의 TTL 을 그만큼 줄인다 — 만료 시각이 같아 관측상 구분되지 않는다.
     * ⚠️ 일부 노드에만 줘야 한다. 전부 주면 그냥 TTL 단축이고 과반이 무너지는 시나리오가 아니다.
     */
    val clockSkewMillis: List<Long> = emptyList(),
) {
    fun skewOf(index: Int): Long = clockSkewMillis.getOrElse(index) { 0L }

    fun uris(): List<RedisURI> = nodes.map {
        val (host, port) = it.trim().split(":")
        RedisURI.Builder.redis(host, port.toInt())
            .withTimeout(Duration.ofMillis(timeoutMillis))
            .build()
    }
}

/**
 * 노드 커넥션 묶음.
 *
 * Lettuce 비동기 API 를 쓰므로 **N 개 노드에 동시에 던지는 데 스레드 풀이 필요 없다.**
 * 블로킹 클라이언트였다면 노드 수만큼 스레드를 띄워야 했다.
 */
class RedLockNodes(
    private val clients: List<RedisClient>,
    val connections: List<StatefulRedisConnection<String, String>>,
    private val skews: List<Long> = emptyList(),
) : AutoCloseable {

    fun skewOf(index: Int): Long = skews.getOrElse(index) { 0L }

    val skewed: Boolean get() = skews.any { it > 0 }

    val size: Int get() = connections.size

    /** 과반. 3 대면 2, 5 대면 3. */
    val quorum: Int get() = size / 2 + 1

    override fun close() {
        connections.forEach { runCatching { it.close() } }
        clients.forEach { runCatching { it.shutdown() } }
    }
}

@Configuration
@EnableConfigurationProperties(RedLockProperties::class)
class RedLockNodesConfig {

    /** 노드마다 독립 커넥션. ⚠️ 타임아웃이 길면 유효 시간(TTL−경과−오차)이 음수가 된다. */
    @Bean(destroyMethod = "close")
    fun redLockNodes(props: RedLockProperties): RedLockNodes {
        val clients = props.uris().map { RedisClient.create(it) }

        return RedLockNodes(
            clients = clients,
            connections = clients.map { it.connect() },
            skews = props.nodes.indices.map { props.skewOf(it) },
        )
    }
}
