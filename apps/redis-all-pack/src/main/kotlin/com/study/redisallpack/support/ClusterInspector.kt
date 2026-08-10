package com.study.redisallpack.support

import org.springframework.data.redis.connection.RedisClusterNode
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import redis.clients.jedis.util.JedisClusterCRC16

@Component
class ClusterInspector(
    private val redisTemplate: StringRedisTemplate,
) {

    fun topology(): List<NodeInfo> = connection().clusterGetNodes().map { it.toInfo() }

    fun locate(key: String): KeyLocation {
        val slot = JedisClusterCRC16.getSlot(key)  // 해시태그 {..} 가 있으면 그 안만 계산한다
        val node = connection().clusterGetNodes()
            .firstOrNull { it.isMaster && it.slotRange?.contains(slot) == true }

        return KeyLocation(key, slot, node?.let { "${it.host}:${it.port}" } ?: "unknown")
    }

    fun distribution(keys: List<String>): Map<String, Int> =
        keys.groupingBy { locate(it).node }.eachCount()

    /** 같은 슬롯으로 묶는다. MGET·트랜잭션이 한 노드에서 끝나는 대신 그 슬롯이 핫스팟이 된다. */
    fun hashTagged(tag: String, key: String) = "{$tag}:$key"

    private fun connection() = redisTemplate.connectionFactory!!.clusterConnection

    private fun RedisClusterNode.toInfo() = NodeInfo(
        id = id ?: "unknown",
        address = "$host:$port",
        role = if (isMaster) "master" else "replica",
        slots = slotRange?.slotsArray?.let { if (it.isEmpty()) null else "${it.first()}-${it.last()}" },
    )

    data class NodeInfo(val id: String, val address: String, val role: String, val slots: String?)

    data class KeyLocation(val key: String, val slot: Int, val node: String)
}
