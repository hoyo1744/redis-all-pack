package com.study.redisallpack.infrastructure.layout

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.study.redisallpack.domain.layout.ItemLayout
import com.study.redisallpack.domain.layout.ItemStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 그룹 하나 = String 키 하나에 JSON 통째.
 *
 * 메모리는 작지만 Redis 는 이 값을 **불투명한 바이트**로만 안다. 아이템 하나를 읽어도 전체가
 * 네트워크를 건너오고, 하나를 바꾸려면 전체를 읽고 → 고치고 → 통째로 덮어써야 한다.
 */
@Component
class JsonStringItemStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : ItemStore {

    override val layout = ItemLayout.JSON_STRING

    override fun keysOf(group: String, itemIds: List<String>) = listOf(key(group))

    override fun writeAll(group: String, items: Map<String, String>) {
        redisTemplate.opsForValue().set(key(group), objectMapper.writeValueAsString(items))
    }

    /** 하나만 필요해도 전부 받아 파싱한다. */
    override fun readOne(group: String, itemId: String): String? = load(group)[itemId]

    override fun readAll(group: String, itemIds: List<String>): Map<String, String> = load(group)

    override fun updateOne(group: String, itemId: String, value: String) {
        val items = load(group).toMutableMap()
        items[itemId] = value
        writeAll(group, items)
    }

    /**
     * ⚠️ 의도적으로 **원자적이지 않다.** GET → 수정 → SET 사이에 다른 요청이 끼면, 그 요청이
     * 바꾼 **다른 아이템**까지 내 SET 이 옛 값으로 덮어쓴다. 아이템이 달라도 키가 하나라 부딪힌다.
     * 막으려면 WATCH/MULTI 나 Lua(`cjson`)로 서버에서 고쳐야 한다.
     */
    override fun increment(group: String, itemId: String) {
        val items = load(group).toMutableMap()
        items[itemId] = ((items[itemId]?.toLongOrNull() ?: 0L) + 1).toString()
        writeAll(group, items)
    }

    override fun delete(group: String, itemIds: List<String>) {
        redisTemplate.delete(key(group))
    }

    private fun load(group: String): Map<String, String> =
        redisTemplate.opsForValue().get(key(group))
            ?.let { objectMapper.readValue(it, MAP_TYPE) }
            .orEmpty()

    private fun key(group: String) = "layout:json:$group"

    private companion object {
        val MAP_TYPE = object : TypeReference<Map<String, String>>() {}
    }
}
