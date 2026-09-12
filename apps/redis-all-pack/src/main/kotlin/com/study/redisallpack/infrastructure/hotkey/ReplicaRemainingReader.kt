package com.study.redisallpack.infrastructure.hotkey

import com.study.config.redis.LettuceClusterConfig
import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingReader
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 읽기를 레플리카로 보낸다. 마스터는 쓰기만 받는다.
 *
 * 키는 [PlainRemainingReader] 와 **완전히 같다.** 슬롯도, 담당 마스터도 그대로다.
 * 달라지는 건 커넥션뿐이다 — `ReadFrom.REPLICA` 커넥션은 GET 을 그 슬롯 마스터의
 * **레플리카에게** 던진다. 키를 쪼개는 게 아니라 **읽는 곳을 바꾸는** 전략이다.
 *
 * ⚠️ 복제가 비동기다. 방금 쓴 값이 레플리카에 아직 없을 수 있다. 남은 자리 표시처럼
 * 조금 낡아도 되는 조회에만 쓸 수 있고, 쓰기 직후 자기 결과를 확인하는 경로에는 못 쓴다.
 */
@Component
class ReplicaRemainingReader(
    @Qualifier(LettuceClusterConfig.REPLICA_TEMPLATE) private val replicaTemplate: StringRedisTemplate,
    private val delegate: PlainRemainingReader,
) : RemainingReader {

    override val strategy = HotKeyStrategy.REPLICA_READ

    /** 레플리카에서 읽는다. 이 한 줄이 전략의 전부다. */
    override fun read(eventId: String): Int? =
        replicaTemplate.opsForValue().get(PlainRemainingReader.key(eventId))?.toIntOrNull()

    /** 쓰기는 마스터로. 레플리카는 쓰기를 받지 않는다. */
    override fun write(eventId: String, remaining: Int) = delegate.write(eventId, remaining)

    override fun evict(eventId: String) = delegate.evict(eventId)
}
