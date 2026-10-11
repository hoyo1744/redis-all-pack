package com.study.redisallpack.domain.layout

/** Redis 서버 쪽 메모리·인코딩을 들여다본다. */
interface MemoryProbe {

    /** 마스터 전부의 `INFO memory` → `used_memory` 합. 키가 여러 노드에 흩어지므로 합으로 본다. */
    fun usedMemory(): Long

    /** `OBJECT ENCODING` — embstr / int / raw / listpack / hashtable … */
    fun encodingOf(key: String): String

    /** `MEMORY USAGE` — 키 하나의 추정 크기(바이트). */
    fun memoryUsageOf(key: String): Long?

    /** 키들이 어느 노드에 몇 개씩 놓였는지. */
    fun nodeDistribution(keys: List<String>): Map<String, Int>
}
