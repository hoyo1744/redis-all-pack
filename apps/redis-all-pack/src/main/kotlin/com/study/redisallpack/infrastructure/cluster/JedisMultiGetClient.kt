package com.study.redisallpack.infrastructure.cluster

import com.study.redisallpack.domain.cluster.MultiGetClient
import org.springframework.stereotype.Component
import redis.clients.jedis.JedisCluster

/**
 * `JedisCluster` 를 직접 쓴다. Spring Data 를 거치지 않는다.
 *
 * Jedis 는 전통적으로 **키 하나로 노드를 정해 그대로 보내는** 방식이라
 * 멀티키를 쪼개주지 않을 가능성이 높다. 그게 맞는지 본다.
 */
@Component
class JedisMultiGetClient(
    private val jedisCluster: JedisCluster,
) : MultiGetClient {

    override val name = "Jedis (JedisCluster)"

    override fun seed(keys: List<String>) {
        keys.forEachIndexed { i, key -> jedisCluster.set(key, ((i + 1) * 10).toString()) }
    }

    override fun multiGet(keys: List<String>): Map<String, String> {
        val values = jedisCluster.mget(*keys.toTypedArray())

        return keys.zip(values).mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
    }
}
