package com.study.config.redis

import io.lettuce.core.ReadFrom
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import org.springframework.beans.factory.annotation.Qualifier
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
        val clusterConfig = RedisClusterConfiguration(props.nodes).apply {
            maxRedirects = props.maxRedirects
        }

        return LettuceConnectionFactory(clusterConfig, clientConfig(readFrom = null))
    }

    /** 기본은 마스터행. 레플리카 템플릿이 생기면서 둘이 되므로 @Primary 로 기본을 못박는다. */
    @Bean
    @Primary
    fun stringRedisTemplate(factory: LettuceConnectionFactory) = StringRedisTemplate(factory)

    /**
     * 레플리카 읽기 전용 커넥션. [ReadFrom.REPLICA] 라 **읽기가 전부 레플리카로** 간다.
     *
     * 팩토리를 따로 두는 이유: ReadFrom 은 커넥션 수준 설정이라 기존 템플릿(마스터행)과
     * 한 팩토리를 공유할 수 없다. 쓰기는 어차피 마스터로만 가므로 이 템플릿은 읽기에만 쓴다.
     *
     * ⚠️ REPLICA_PREFERRED 가 아니라 REPLICA 다. 실험에서 "레플리카가 없으면 조용히
     * 마스터로 폴백" 하면 측정이 오염된다. 레플리카를 안 띄웠으면 읽기가 실패해야 맞다.
     */
    @Bean(REPLICA_FACTORY)
    fun replicaConnectionFactory(props: RedisClusterProperties): LettuceConnectionFactory {
        val clusterConfig = RedisClusterConfiguration(props.nodes).apply {
            maxRedirects = props.maxRedirects
        }

        return LettuceConnectionFactory(clusterConfig, clientConfig(ReadFrom.REPLICA))
    }

    @Bean(REPLICA_TEMPLATE)
    fun replicaRedisTemplate(
        @Qualifier(REPLICA_FACTORY) factory: LettuceConnectionFactory,
    ) = StringRedisTemplate(factory)

    private fun clientConfig(readFrom: ReadFrom?): LettuceClientConfiguration {
        val topology = ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(Duration.ofSeconds(10))
            .enableAllAdaptiveRefreshTriggers()
            .build()

        val builder = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(2))
            .clientOptions(
                ClusterClientOptions.builder()
                    .topologyRefreshOptions(topology)
                    .validateClusterNodeMembership(false)
                    .build(),
            )

        readFrom?.let { builder.readFrom(it) }

        return builder.build()
    }

    companion object {
        const val REPLICA_FACTORY = "replicaConnectionFactory"
        const val REPLICA_TEMPLATE = "replicaRedisTemplate"
    }
}
