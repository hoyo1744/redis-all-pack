package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * 키를 N 개로 쪼갠다. `event:remaining:BF#0` ~ `#N-1` 이 서로 다른 슬롯에 떨어지므로
 * **읽기가 여러 노드로 퍼진다.**
 *
 * ⚠️ **해시태그를 쓰면 안 된다.** `{coupon:BF}:0` 처럼 묶으면 전부 같은 슬롯으로 가서
 * 샤딩한 의미가 사라진다. 핫키를 흩으려면 오히려 태그를 **빼야** 한다.
 *
 * ⚠️ 쓰기 비용이 N 배다. 잔여 수량이 바뀌면 샤드 전부를 갱신해야 하고,
 * 그 사이 샤드마다 값이 다를 수 있다.
 */
@Component
class ShardedRemainingReader(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${hotkey.shards:16}") private val shards: Int,
) : RemainingReader {

    override val strategy = HotKeyStrategy.KEY_SHARDING

    /** 읽을 때는 아무 샤드나 하나. 그래야 요청이 노드들로 흩어진다. */
    override fun read(eventId: String): Int? {
        val shard = ThreadLocalRandom.current().nextInt(shards)
        return redisTemplate.opsForValue().get(key(eventId, shard))?.toIntOrNull()
    }

    /** 쓸 때는 전부. 이게 샤딩의 대가다. */
    override fun write(eventId: String, remaining: Int) {
        val value = remaining.toString()
        repeat(shards) { i ->
            redisTemplate.opsForValue().set(key(eventId, i), value, jitteredTtl())
        }
    }

    override fun evict(eventId: String) {
        repeat(shards) { i -> redisTemplate.delete(key(eventId, i)) }
    }

    /** 태그 없이 샤드 번호를 그대로 붙인다. 슬롯이 갈려야 하기 때문이다. */
    private fun key(eventId: String, shard: Int) = "event:remaining:$eventId#$shard"

    /**
     * TTL 에 0~10% 지터를 준다. 같은 TTL 로 심으면 한 번의 write 로 태어난 조각 N 개가
     * **거의 동시에 만료**되고, 그 순간 미스가 한꺼번에 터진다 — 조각판 스탬피드다.
     * 만료 시각을 흩뜨리면 조각이 하나씩 낡아서 원본행도 하나씩 발생한다.
     */
    private fun jitteredTtl(): Duration {
        val base = PlainRemainingReader.TTL.toMillis()
        return Duration.ofMillis(base + ThreadLocalRandom.current().nextLong(base / 10 + 1))
    }
}
