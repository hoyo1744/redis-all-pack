package com.study.config.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/** 같은 값을 JSON 과 Smile 로 넣어 크기·속도를 비교한다. */
@Configuration
class SerializationConfig {

    @Bean
    fun smileObjectMapper(): ObjectMapper = ObjectMapper(SmileFactory()).registerKotlinModule()

    @Bean
    fun jsonRedisTemplate(factory: LettuceConnectionFactory): RedisTemplate<String, Any> =
        RedisTemplate<String, Any>().apply {
            connectionFactory = factory
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer()
            afterPropertiesSet()
        }

    /** 값이 바이트로 들어간다. redis-cli 로 보면 안 읽히는 게 정상이다. */
    @Bean
    fun binaryRedisTemplate(factory: LettuceConnectionFactory): RedisTemplate<String, ByteArray> =
        RedisTemplate<String, ByteArray>().apply {
            connectionFactory = factory
            keySerializer = StringRedisSerializer()
            afterPropertiesSet()
        }
}
