package com.study.redisallpack.application.cluster

import com.study.redisallpack.domain.cluster.ClientComparison
import com.study.redisallpack.domain.cluster.MultiGetClient
import com.study.redisallpack.domain.cluster.MultiGetResult
import com.study.redisallpack.domain.cluster.SlotInspector
import org.springframework.stereotype.Component

@Component
class ClusterFacade(
    private val slotInspector: SlotInspector,
    private val multiGetClients: List<MultiGetClient>,
) {

    /**
     * 같은 이벤트 목록을 **해시태그 유무만 바꿔** 조회한다.
     * 태그가 없으면 슬롯이 갈려 CROSSSLOT, 있으면 한 슬롯으로 묶여 성공한다.
     */
    fun multiGet(eventIds: List<String>, tagged: Boolean): MultiGetResult {
        val keys = eventIds.map { key(it, tagged) }

        slotInspector.seed(keys)
        return slotInspector.multiGet(keys)
    }

    /**
     * **같은 요청을 클라이언트 셋에게 각각 보낸다.**
     *
     * `redis-cli` 로는 CROSSSLOT 이 나는데 애플리케이션에서는 안 나는 경우가 있다.
     * 클라이언트가 슬롯별로 쪼개 보내고 결과를 합치기 때문이다.
     * 그 동작이 클라이언트마다 같은지 다른지를 눈으로 확인한다.
     */
    fun compareClients(eventIds: List<String>, tagged: Boolean): ClientComparison {
        val keys = eventIds.map { key(it, tagged) }
        val slots = keys.map { MultiGetResult.KeySlot(it, slotInspector.slotOf(it)) }

        val results = multiGetClients.map { client ->
            // 클라이언트마다 직렬화가 달라 자기가 심은 값을 자기가 읽게 한다.
            runCatching { client.seed(keys) }

            val started = System.nanoTime()
            runCatching { client.multiGet(keys) }.fold(
                onSuccess = {
                    ClientComparison.ClientResult(
                        client = client.name,
                        succeeded = true,
                        values = it,
                        elapsedMillis = elapsed(started),
                    )
                },
                onFailure = {
                    ClientComparison.ClientResult(
                        client = client.name,
                        succeeded = false,
                        error = rootCause(it),
                        elapsedMillis = elapsed(started),
                    )
                },
            )
        }

        return ClientComparison(slots, slots.map { it.slot }.distinct().size, results)
    }

    private fun elapsed(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    /** 래핑을 벗겨야 CROSSSLOT 인지 다른 이유인지 구분된다. */
    private fun rootCause(t: Throwable): String {
        var cause: Throwable = t
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!

        return "${cause.javaClass.simpleName}: ${cause.message}"
    }

    /** 태그를 씌우면 슬롯 계산이 `event` 만으로 이뤄져 전부 같은 슬롯이 된다. */
    private fun key(eventId: String, tagged: Boolean) =
        if (tagged) "{event}:remaining:$eventId" else "event:remaining:$eventId"
}
