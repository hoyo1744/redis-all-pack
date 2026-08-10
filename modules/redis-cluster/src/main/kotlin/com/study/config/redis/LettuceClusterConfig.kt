package com.study.config.redis

import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

@Configuration
@EnableConfigurationProperties(RedisClusterProperties::class)
class LettuceClusterConfig {

    /**
     * adaptive refresh 를 켠다. 끄면 failover 후에도 옛 토폴로지를 붙들고 있어
     * 3주차에서 "락은 살아있는데 노드가 바뀐" 상황을 재현할 수 없다.
     */
    @Bean
    @Primary
    fun lettuceConnectionFactory(props: RedisClusterProperties): LettuceConnectionFactory {
        val topology = ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(Duration.ofSeconds(10))
            .enableAllAdaptiveRefreshTriggers()
            .build()

        val clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(2))
            .clientOptions(
                ClusterClientOptions.builder()
                    .topologyRefreshOptions(topology)
                    .validateClusterNodeMembership(false)
                    .build(),
            )
            .build()

        val clusterConfig = RedisClusterConfiguration(props.nodes).apply {
            maxRedirects = props.maxRedirects
        }

        return LettuceConnectionFactory(clusterConfig, clientConfig)
    }

    @Bean
    fun stringRedisTemplate(factory: LettuceConnectionFactory) = StringRedisTemplate(factory)
}
