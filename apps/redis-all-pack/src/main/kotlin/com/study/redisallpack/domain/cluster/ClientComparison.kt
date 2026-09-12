package com.study.redisallpack.domain.cluster

/**
 * 같은 멀티키 요청을 클라이언트별로 보내 **누가 대신 쪼개주는지** 본다.
 *
 * `redis-cli` 로 슬롯이 갈린 MGET 을 치면 CROSSSLOT 이 난다. 그런데 애플리케이션에서는
 * 안 나는 경우가 있다 — 클라이언트가 슬롯별로 나눠 보내고 결과를 합치기 때문이다.
 * 그 동작이 클라이언트마다 다른지 확인하는 것이 이 실험이다.
 */
data class ClientComparison(
    val keys: List<MultiGetResult.KeySlot>,
    val distinctSlots: Int,
    val results: List<ClientResult>,
) {
    data class ClientResult(
        val client: String,
        /** 쪼개서 성공했는가. false 면 CROSSSLOT 을 그대로 돌려준 것이다. */
        val succeeded: Boolean,
        val values: Map<String, String>? = null,
        val error: String? = null,
        val elapsedMillis: Long,
    )
}
