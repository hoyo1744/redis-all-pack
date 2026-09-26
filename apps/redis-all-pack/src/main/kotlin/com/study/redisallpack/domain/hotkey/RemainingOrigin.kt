package com.study.redisallpack.domain.hotkey

/**
 * 잔여 수량의 **권위 있는 원본**. 캐시가 아니라 진짜 값(DB)이다.
 *
 * PER 은 주사위가 당첨된 순간 읽기 도중에 직접 재계산하고 그 **소요 시간(delta)** 을 재야 한다.
 * 그래서 캐시 미스를 facade 에 넘기는 다른 전략과 달리, 원본 접근 경로를 자기가 들고 있어야 한다.
 */
fun interface RemainingOrigin {

    /** 캐시를 거치지 않고 원본에서 잔여 수량을 읽는다. 비쌀 수 있는 경로다. */
    fun read(eventId: String): Int
}
