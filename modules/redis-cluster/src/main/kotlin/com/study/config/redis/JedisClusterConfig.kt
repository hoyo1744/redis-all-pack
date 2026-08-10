package com.study.config.redis

import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Connection
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
import java.time.Duration

@Configuration
class JedisClusterConfig {

    @Bean(destroyMethod = "close")
    fun jedisCluster(props: RedisClusterProperties): JedisCluster {
        val nodes = props.hostPorts().map { (host, port) -> HostAndPort(host, port) }.toSet()

        val clientConfig = DefaultJedisClientConfig.builder()
            .connectionTimeoutMillis(2000)
            .socketTimeoutMillis(2000)
            .build()

        val pool = GenericObjectPoolConfig<Connection>().apply {
            maxTotal = 64
            maxIdle = 32
            minIdle = 8
        }

        return JedisCluster(nodes, clientConfig, props.maxRedirects, Duration.ofSeconds(2), pool)
    }
}
