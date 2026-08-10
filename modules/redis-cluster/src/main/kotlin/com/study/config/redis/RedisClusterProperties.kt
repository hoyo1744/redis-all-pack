package com.study.config.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "spring.data.redis.cluster")
data class RedisClusterProperties @ConstructorBinding constructor(
    val nodes: List<String>,
    val maxRedirects: Int = 3,
) {
    fun hostPorts(): List<Pair<String, Int>> = nodes.map {
        val (host, port) = it.trim().split(":")
        host to port.toInt()
    }

    fun redissonUris(): Array<String> = nodes.map { "redis://${it.trim()}" }.toTypedArray()
}
