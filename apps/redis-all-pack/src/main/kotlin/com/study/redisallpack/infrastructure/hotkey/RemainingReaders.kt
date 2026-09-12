package com.study.redisallpack.infrastructure.hotkey

import com.study.redisallpack.domain.hotkey.HotKeyStrategy
import com.study.redisallpack.domain.hotkey.RemainingReader
import org.springframework.stereotype.Component

/** 전략을 요청마다 골라 쓴다. 동시에 다 띄워야 같은 조건에서 비교가 된다. */
@Component
class RemainingReaders(
    readers: List<RemainingReader>,
) {
    private val byStrategy = readers.associateBy { it.strategy }

    val available: Set<HotKeyStrategy> get() = byStrategy.keys

    fun of(strategy: HotKeyStrategy): RemainingReader =
        byStrategy[strategy] ?: throw IllegalArgumentException(
            "사용할 수 없는 전략입니다. strategy=$strategy 가능=$available",
        )
}
