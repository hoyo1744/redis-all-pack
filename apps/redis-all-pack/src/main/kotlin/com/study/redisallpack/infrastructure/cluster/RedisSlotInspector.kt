package com.study.redisallpack.infrastructure.cluster

import com.study.redisallpack.domain.cluster.MultiGetResult
import com.study.redisallpack.domain.cluster.SlotInspector
import io.lettuce.core.cluster.SlotHash
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisSlotInspector(
    private val redisTemplate: StringRedisTemplate,
) : SlotInspector {

    /**
     * 서버에 `CLUSTER KEYSLOT` 을 묻지 않는다. 클라이언트가 같은 규칙(CRC16 & 0x3FFF)을
     * 독립적으로 돌린다. 그래야 통신 없이 라우팅이 성립한다.
     */
    override fun slotOf(key: String): Int = SlotHash.getSlot(key)

    override fun seed(keys: List<String>) {
        keys.forEachIndexed { i, key ->
            redisTemplate.opsForValue().set(key, ((i + 1) * 10).toString())
        }
    }

    override fun multiGet(keys: List<String>): MultiGetResult {
        val slots = keys.map { MultiGetResult.KeySlot(it, slotOf(it)) }
        val distinct = slots.map { it.slot }.distinct().size

        log.info("[CROSSSLOT 실험] 키 {}개", keys.size)
        slots.forEach { log.info("    {}  →  slot {}", it.key, it.slot) }
        log.info("    서로 다른 슬롯 {}개 → MGET 시도", distinct)

        return try {
            val values = redisTemplate.opsForValue().multiGet(keys).orEmpty()
            val paired = keys.zip(values).mapNotNull { (k, v) -> v?.let { k to it } }.toMap()

            log.info("    ✓ 성공 — {}", paired)
            MultiGetResult(slots, distinct, succeeded = true, values = paired)
        } catch (e: DataAccessException) {
            // 슬롯이 갈리면 여기로 온다. 명령 하나는 노드 하나에서만 실행되기 때문이다.
            val message = e.mostSpecificCause.message ?: e.toString()

            log.warn("    ✗ 실패 — {}", message)
            MultiGetResult(slots, distinct, succeeded = false, error = message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(RedisSlotInspector::class.java)
    }
}
