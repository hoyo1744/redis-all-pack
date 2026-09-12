package com.study.redisallpack.infrastructure.cluster

import com.study.redisallpack.domain.cluster.MultiGetClient
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Spring Data Redis 의 기본 클라이언트. `RedisTemplate` 을 그대로 쓴다.
 *
 * Lettuce 코어는 클러스터에서 멀티키 명령을 슬롯별로 쪼개는 기능을 갖고 있는데,
 * **Spring Data 가 감싸면서 그 경로를 타는지가 이 실험의 질문이다.**
 */
@Component
class LettuceMultiGetClient(
    private val redisTemplate: StringRedisTemplate,
) : MultiGetClient {

    override val name = "Lettuce (RedisTemplate)"

    override fun seed(keys: List<String>) {
        keys.forEachIndexed { i, key -> redisTemplate.opsForValue().set(key, ((i + 1) * 10).toString()) }
    }

    override fun multiGet(keys: List<String>): Map<String, String> {
        val values = redisTemplate.opsForValue().multiGet(keys).orEmpty()

        return keys.zip(values).mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
    }
}
