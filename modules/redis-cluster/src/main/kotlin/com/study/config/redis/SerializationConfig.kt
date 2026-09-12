package com.study.config.redis

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/** 같은 값을 JSON 과 Smile 로 넣어 크기·속도를 비교한다. */
@Configuration
class SerializationConfig {

    // ⚠️ Smile ObjectMapper 를 빈으로 등록하면 안 된다.
    //
    // `ObjectMapper` 타입 빈이 하나라도 있으면 스프링 부트의 JacksonAutoConfiguration 이
    // `@ConditionalOnMissingBean` 때문에 물러난다. 그러면 **HTTP 메시지 컨버터가 그 Smile 매퍼를 쓴다.**
    // 응답이 전부 바이너리로 나가고, JSON 요청 본문은 파싱에 실패해 400 이 된다.
    // KafkaConfig 의 `jsonMessageConverter(objectMapper)` 도 이걸 받아 이름과 달리 Smile 로 동작한다.
    //
    // Smile 이 필요한 곳은 Redis 직렬화뿐이고, [SmileRedisSerializer] 가 자기 매퍼를 직접 만든다.
    // 4주차 비교에서 매퍼를 공유해야 하면 `ObjectMapper` 가 아닌 전용 타입으로 감싸서 등록할 것.

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
