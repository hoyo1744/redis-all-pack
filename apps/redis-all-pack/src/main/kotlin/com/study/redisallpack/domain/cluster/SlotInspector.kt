package com.study.redisallpack.domain.cluster

/**
 * 슬롯이 갈리면 멀티키 연산이 어떻게 되는지 눈으로 보기 위한 진단용 포트.
 * 운영 경로가 아니라 실험 경로다.
 */
interface SlotInspector {

    /** 키가 어느 슬롯에 떨어지는지. 서버에 묻지 않고 클라이언트가 직접 계산한다. */
    fun slotOf(key: String): Int

    /** 값을 심어둔다. 단일 키 쓰기는 슬롯이 갈려도 항상 성공한다. */
    fun seed(keys: List<String>)

    /** MGET 을 시도한다. 슬롯이 갈리면 CROSSSLOT 으로 실패한다. */
    fun multiGet(keys: List<String>): MultiGetResult
}

data class MultiGetResult(
    val keys: List<KeySlot>,
    val distinctSlots: Int,
    val succeeded: Boolean,
    val values: Map<String, String>? = null,
    val error: String? = null,
) {
    data class KeySlot(val key: String, val slot: Int)
}
