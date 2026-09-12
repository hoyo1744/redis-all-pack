package com.study.redisallpack.domain.lock

interface FenceGenerator {

    /** 어느 구현이 붙어 있는지. 측정 결과 해석에 필요하다. */
    val source: String

    /**
     * **1 이상**이고, 같은 키에 대해 호출할 때마다 커진다. 연속일 필요는 없다.
     *
     * ⚠️ 하한이 1 인 게 중요하다. 자원 검사가 `fence > lastFence` 이고 `lastFence` 초기값이 0 이라
     * 0 을 돌려주면 첫 발급이 자기 자신에게 막힌다. ZooKeeper 순차 znode 는 0 부터 시작한다.
     */
    fun next(key: String): Long
}
