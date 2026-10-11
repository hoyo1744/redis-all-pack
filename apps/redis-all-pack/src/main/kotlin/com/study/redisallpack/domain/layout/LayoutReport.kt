package com.study.redisallpack.domain.layout

/** 그룹 [groups] 개 × 아이템 [items] 개를 넣었을 때 서버 메모리가 얼마나 늘었나. */
data class MemoryReport(
    val layout: ItemLayout,
    val groups: Int,
    val items: Int,
    val valueBytes: Int,
    /** 실제로 생긴 Redis 키 수. */
    val keys: Int,
    /** `used_memory` 증가분(마스터 합). */
    val usedBytes: Long,
    val bytesPerItem: Double,
    /** 첫 그룹 첫 키의 인코딩. */
    val encoding: String,
    /** 첫 그룹이 차지한 키들의 `MEMORY USAGE` 합. */
    val memoryUsagePerGroup: Long?,
)

/** 연산별 평균 지연(µs). 앱 안에서 잰 왕복 시간이라 절대값보다 **비율**을 볼 것. */
data class OpsReport(
    val layout: ItemLayout,
    val items: Int,
    val valueBytes: Int,
    val iterations: Int,
    val readOneMicros: Double,
    val readAllMicros: Double,
    val updateOneMicros: Double,
    /** 그룹 하나의 키가 놓인 노드별 개수. */
    val nodes: Map<String, Int>,
)

/** 스레드 [threads] 개가 서로 **다른 아이템**을 [perThread] 번씩 올렸을 때 몇 번이 살아남았나. */
data class ConcurrencyReport(
    val layout: ItemLayout,
    val threads: Int,
    val perThread: Int,
    val expected: Long,
    val actual: Long,
    val lost: Long,
)
