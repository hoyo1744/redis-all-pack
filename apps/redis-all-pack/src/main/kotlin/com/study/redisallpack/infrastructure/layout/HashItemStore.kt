package com.study.redisallpack.infrastructure.layout

import com.study.redisallpack.domain.layout.ItemLayout
import com.study.redisallpack.domain.layout.ItemStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 그룹 하나 = Hash 키 하나, 아이템 = 필드.
 *
 * 필드 수 ≤ `hash-max-listpack-entries` 이고 모든 값 ≤ `hash-max-listpack-value` 바이트면
 * **listpack** 으로 저장된다. 필드·값이 포인터 없이 한 덩어리 메모리에 붙어 있어서 키 N 개로
 * 쪼갤 때 키마다 붙던 오버헤드가 사라진다. 대신 필드 조회가 그 덩어리를 훑는 O(N) 이다.
 */
@Component
class HashItemStore(
    private val redisTemplate: StringRedisTemplate,
) : ItemStore {

    override val layout = ItemLayout.HASH

    override fun keysOf(group: String, itemIds: List<String>) = listOf(key(group))

    override fun writeAll(group: String, items: Map<String, String>) {
        redisTemplate.opsForHash<String, String>().putAll(key(group), items)
    }

    override fun readOne(group: String, itemId: String): String? =
        redisTemplate.opsForHash<String, String>().get(key(group), itemId)

    override fun readAll(group: String, itemIds: List<String>): Map<String, String> =
        redisTemplate.opsForHash<String, String>().entries(key(group))

    /** 필드 하나만 바꾼다. 나머지 필드는 건드리지 않는다. */
    override fun updateOne(group: String, itemId: String, value: String) {
        redisTemplate.opsForHash<String, String>().put(key(group), itemId, value)
    }

    /** HINCRBY 도 서버에서 원자적이다. 같은 키 안의 다른 필드를 동시에 올려도 안 섞인다. */
    override fun increment(group: String, itemId: String) {
        redisTemplate.opsForHash<String, String>().increment(key(group), itemId, 1)
    }

    override fun delete(group: String, itemIds: List<String>) {
        redisTemplate.delete(key(group))
    }

    private fun key(group: String) = "layout:hash:$group"
}
