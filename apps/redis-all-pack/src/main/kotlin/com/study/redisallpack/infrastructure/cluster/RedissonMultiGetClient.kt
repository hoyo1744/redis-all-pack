package com.study.redisallpack.infrastructure.cluster

import com.study.redisallpack.domain.cluster.MultiGetClient
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.stereotype.Component

/**
 * Redisson 의 `RBuckets` 를 쓴다.
 *
 * 내부적으로 `readBatchedAsync` 를 타는데, 그 구현이 `calcSlot` 으로 키를 슬롯별로 묶어
 * `CommandBatchService` 로 나눠 보낸 뒤 `SlotCallback` 으로 합친다. (jar 확인)
 * **그래서 쪼개줄 것으로 예상되지만 실제로 그런지 본다.**
 *
 * ⚠️ 코덱을 맞춰야 한다. 기본 코덱은 직렬화가 달라 다른 클라이언트가 심은 값을 못 읽는다.
 */
@Component
class RedissonMultiGetClient(
    private val redissonClient: RedissonClient,
) : MultiGetClient {

    override val name = "Redisson (RBuckets)"

    override fun seed(keys: List<String>) {
        keys.forEachIndexed { i, key ->
            redissonClient.getBucket<String>(key, StringCodec.INSTANCE).set(((i + 1) * 10).toString())
        }
    }

    override fun multiGet(keys: List<String>): Map<String, String> =
        redissonClient.getBuckets(StringCodec.INSTANCE)
            .get<String>(*keys.toTypedArray())
            .filterValues { it != null }
}
