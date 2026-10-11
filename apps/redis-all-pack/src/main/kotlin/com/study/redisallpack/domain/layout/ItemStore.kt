package com.study.redisallpack.domain.layout

/**
 * 같은 연산을 **저장 모양만 바꿔** 수행한다. 실험은 이 포트만 보고, 모양은 구현체가 정한다.
 *
 * `group` 은 아이템 묶음 하나(예: 이벤트 하나의 쿠폰 100 종), `itemId` 는 그 안의 아이템이다.
 */
interface ItemStore {

    val layout: ItemLayout

    /** 이 그룹이 실제로 차지하는 Redis 키. STRING_KEYS 면 N 개, 나머지는 1 개. */
    fun keysOf(group: String, itemIds: List<String>): List<String>

    fun writeAll(group: String, items: Map<String, String>)

    fun readOne(group: String, itemId: String): String?

    fun readAll(group: String, itemIds: List<String>): Map<String, String>

    fun updateOne(group: String, itemId: String, value: String)

    /** 숫자 값 하나를 1 올린다. 동시에 부르면 저장 모양에 따라 **갱신이 유실될 수 있다.** */
    fun increment(group: String, itemId: String)

    fun delete(group: String, itemIds: List<String>)
}
