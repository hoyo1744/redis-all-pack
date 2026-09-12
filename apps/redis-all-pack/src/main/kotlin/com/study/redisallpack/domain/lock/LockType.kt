package com.study.redisallpack.domain.lock

/** 같은 락을 네 가지로 만들어 놓고 나란히 잰다. 셋은 대기하는 방식이 다르다. */
enum class LockType(val description: String) {

    NONE("락 없음 — 초과 발급 재현용 기준선"),

    SPIN("Lettuce 스핀락 — SET NX 재시도"),

    REDISSON("Redisson RLock — pub/sub 대기"),

    REDLOCK("RedLock — 독립 노드 과반"),
    ;

    fun usesLock(): Boolean = this != NONE
}
