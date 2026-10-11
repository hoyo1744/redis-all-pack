package com.study.redisallpack.infrastructure.layout

import com.study.redisallpack.domain.layout.ItemLayout
import com.study.redisallpack.domain.layout.ItemStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 아이템마다 키 하나. 가장 흔하고 가장 단순한 모양이다.
 *
 * 해시태그를 **안 씌운다.** 그래서 한 그룹의 키 N 개가 슬롯 여러 개, 노드 여러 대로 흩어진다.
 * 부하는 고르게 나뉘지만, `MGET` 한 번이 클러스터 안에서는 여러 노드 왕복으로 쪼개진다.
 */
@Component
class StringKeysItemStore(
    private val redisTemplate: StringRedisTemplate,
) : ItemStore {

    override val layout = ItemLayout.STRING_KEYS

    override fun keysOf(group: String, itemIds: List<String>) = itemIds.map { key(group, it) }

    override fun writeAll(group: String, items: Map<String, String>) {
        // 슬롯이 갈려도 스프링이 키별로 나눠 보낸다(클러스터 MSET 은 한 슬롯만 받는다).
        redisTemplate.opsForValue().multiSet(items.mapKeys { (id, _) -> key(group, id) })
    }

    override fun readOne(group: String, itemId: String): String? =
        redisTemplate.opsForValue().get(key(group, itemId))

    override fun readAll(group: String, itemIds: List<String>): Map<String, String> {
        val values = redisTemplate.opsForValue().multiGet(keysOf(group, itemIds)).orEmpty()
        return itemIds.zip(values).mapNotNull { (id, v) -> v?.let { id to it } }.toMap()
    }

    override fun updateOne(group: String, itemId: String, value: String) {
        redisTemplate.opsForValue().set(key(group, itemId), value)
    }

    /** INCR 은 서버에서 원자적으로 돈다. 키가 아이템마다 따로라 다른 아이템과 부딪힐 일도 없다. */
    override fun increment(group: String, itemId: String) {
        redisTemplate.opsForValue().increment(key(group, itemId))
    }

    override fun delete(group: String, itemIds: List<String>) {
        redisTemplate.delete(keysOf(group, itemIds))
    }

    private fun key(group: String, itemId: String) = "layout:str:$group:$itemId"
}
