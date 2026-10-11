package com.study.redisallpack.domain.layout

/** 실험 결과를 어딘가(지표·로그)에 남긴다. 실험 로직은 어디에 남는지 모른다. */
interface LayoutResultRecorder {
    fun record(report: MemoryReport)
    fun record(report: OpsReport)
    fun record(report: ConcurrencyReport)
}
