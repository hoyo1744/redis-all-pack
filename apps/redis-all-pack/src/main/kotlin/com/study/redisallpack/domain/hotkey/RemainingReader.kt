package com.study.redisallpack.domain.hotkey

/**
 * 잔여 수량을 어디서 읽을지. 전략마다 구현이 다르고 도메인은 이 인터페이스만 안다.
 */
interface RemainingReader {

    val strategy: HotKeyStrategy

    /** @return 캐시에 없으면 null. 호출자가 원본으로 간다. */
    fun read(eventId: String): Int?

    fun write(eventId: String, remaining: Int)

    fun evict(eventId: String)
}
