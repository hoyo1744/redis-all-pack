package com.study.redisallpack.domain.coupon

/** 쓰기 방식. 락과 직교하는 축이라 따로 껐다 켠다. */
enum class WriteMode(val description: String) {

    /** 읽고 → 계산하고 → 쓴다. 락이 없으면 lost update 가 난다. */
    PLAIN("읽고-계산하고-쓰기 (lost update 취약)"),

    /**
     * 펜싱 토큰을 보되 비교와 쓰기가 갈라져 있다.
     * 락이 뚫리면 `last_fence` 에도 lost update 가 나서 여전히 샌다 — [FENCED] 와 비교용.
     */
    FENCED_NAIVE("펜싱 토큰 + 읽고-비교하고-쓰기 (원자성 없음)"),

    /** 조건부 UPDATE 한 방. 락이 없어도 초과 발급이 불가능하다. */
    ATOMIC("조건부 UPDATE (락 없이도 정확)"),

    /** 조건부 UPDATE + `last_fence < :fence` 로 지각 쓰기까지 거부. */
    FENCED("조건부 UPDATE + 펜싱 토큰"),
    ;

    fun usesFence(): Boolean = this == FENCED || this == FENCED_NAIVE
}
