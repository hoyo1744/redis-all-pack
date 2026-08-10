package com.study.config.redis

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.redisson.config.ReadMode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonClusterConfig {

    /** readMode 가 SLAVE 면 복제 지연 때문에 방금 잡은 락이 안 보이는 구간이 생긴다. */
    @Bean(destroyMethod = "shutdown")
    fun redissonClient(props: RedisClusterProperties): RedissonClient {
        val config = Config().apply {
            useClusterServers()
                .addNodeAddress(*props.redissonUris())
                .setReadMode(ReadMode.MASTER)
                .setScanInterval(2000)
                .setTimeout(2000)
                .setRetryAttempts(3)
        }

        return Redisson.create(config)
    }
}
