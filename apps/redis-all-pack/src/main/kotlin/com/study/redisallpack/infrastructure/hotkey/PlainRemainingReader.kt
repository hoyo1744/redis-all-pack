package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingReader
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 완화 없음. 키 하나를 그대로 쓴다.
 *
 * `event:remaining:BF` 는 CRC16 으로 슬롯 하나에 고정되므로 **조회가 전부 한 노드로 간다.**
 * 이게 기준선이다.
 */
@Component
class PlainRemainingReader(
    private val redisTemplate: StringRedisTemplate,
) : RemainingReader {

    override val strategy = HotKeyStrategy.NONE

    override fun read(eventId: String): Int? =
        redisTemplate.opsForValue().get(key(eventId))?.toIntOrNull()

    override fun write(eventId: String, remaining: Int) {
        redisTemplate.opsForValue().set(key(eventId), remaining.toString(), TTL)
    }

    override fun evict(eventId: String) {
        redisTemplate.delete(key(eventId))
    }

    companion object {
        val TTL: Duration = Duration.ofSeconds(30)
        fun key(eventId: String) = "event:remaining:$eventId"
    }
}
