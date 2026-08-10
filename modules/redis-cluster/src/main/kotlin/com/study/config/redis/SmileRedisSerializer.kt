package com.study.config.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.data.redis.serializer.RedisSerializer

/** 반복되는 키 이름을 백레퍼런스로 공유하므로 같은 모양의 객체가 많을수록 이득이 커진다. */
class SmileRedisSerializer<T>(
    private val type: Class<T>,
) : RedisSerializer<T> {

    private val mapper: ObjectMapper = ObjectMapper(SmileFactory()).registerKotlinModule()

    override fun serialize(value: T?): ByteArray? = value?.let { mapper.writeValueAsBytes(it) }

    override fun deserialize(bytes: ByteArray?): T? =
        bytes?.takeIf { it.isNotEmpty() }?.let { mapper.readValue(it, type) }
}
