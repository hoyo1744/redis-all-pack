package com.study.redisallpack.infrastructure.layout

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.study.redisallpack.domain.layout.ItemLayout
import com.study.redisallpack.domain.layout.ItemStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * [JsonStringItemStore] 와 같은 모양을 **Smile** 로 넣는다. 비교하려는 건 포맷 하나뿐이다.
 *
 * ⚠️ 값이 UTF-8 이 아닌 바이트라 StringRedisTemplate 의 `opsForValue()` 로는 못 넣는다.
 * `binaryRedisTemplate` 빈은 값 직렬화기를 안 정해 기본값(JDK 직렬화)이 헤더를 덧붙일 수 있어서,
 * 커넥션 수준으로 내려가 **바이트를 그대로** GET/SET 한다.
 *
 * ⚠️ Smile 매퍼를 빈으로 등록하지 않는다 — `SerializationConfig` 주석 참고(HTTP 컨버터가 오염된다).
 */
@Component
class SmileStringItemStore(
    private val redisTemplate: StringRedisTemplate,
) : ItemStore {

    private val smile = ObjectMapper(SmileFactory())

    override val layout = ItemLayout.SMILE_STRING

    override fun keysOf(group: String, itemIds: List<String>) = listOf(key(group))

    override fun writeAll(group: String, items: Map<String, String>) {
        val bytes = smile.writeValueAsBytes(items)
        redisTemplate.execute { it.stringCommands().set(key(group).toByteArray(), bytes) }
    }

    override fun readOne(group: String, itemId: String): String? = load(group)[itemId]

    override fun readAll(group: String, itemIds: List<String>): Map<String, String> = load(group)

    override fun updateOne(group: String, itemId: String, value: String) {
        val items = load(group).toMutableMap()
        items[itemId] = value
        writeAll(group, items)
    }

    /** JSON 과 똑같이 원자적이지 않다. 포맷을 바꿔도 "키 하나에 통째"의 동시성 문제는 그대로다. */
    override fun increment(group: String, itemId: String) {
        val items = load(group).toMutableMap()
        items[itemId] = ((items[itemId]?.toLongOrNull() ?: 0L) + 1).toString()
        writeAll(group, items)
    }

    override fun delete(group: String, itemIds: List<String>) {
        redisTemplate.delete(key(group))
    }

    private fun load(group: String): Map<String, String> =
        redisTemplate.execute { it.stringCommands().get(key(group).toByteArray()) }
            ?.let { smile.readValue(it, MAP_TYPE) }
            .orEmpty()

    private fun key(group: String) = "layout:smile:$group"

    private companion object {
        val MAP_TYPE = object : TypeReference<Map<String, String>>() {}
    }
}
