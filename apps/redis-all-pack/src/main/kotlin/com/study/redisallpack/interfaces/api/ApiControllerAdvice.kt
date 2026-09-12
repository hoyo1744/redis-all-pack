package com.study.redisallpack.interfaces.api

import com.study.redisallpack.domain.coupon.StaleFenceException
import com.study.redisallpack.domain.lock.LockAcquisitionException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 실패를 **종류별로 다른 상태 코드**로 내보낸다.
 *
 * 부하 도구가 응답 코드만 보고 집계할 수 있어야 한다. 전부 500 으로 뭉뚱그리면
 * *"락을 못 잡아서 못 받은 것"* 과 *"소진돼서 못 받은 것"* 과 *"지각 쓰기가 막힌 것"* 이
 * 한 덩어리가 돼 측정이 무의미해진다. 셋은 완전히 다른 일이다.
 */
@RestControllerAdvice
class ApiControllerAdvice {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 대기 시간 안에 락을 못 잡았다. 선착순에서는 **정상 경로**다 — 늦게 온 사람이다. */
    @ExceptionHandler(LockAcquisitionException::class)
    fun handleLockAcquisition(e: LockAcquisitionException) =
        problem(HttpStatus.TOO_MANY_REQUESTS, "LOCK_TIMEOUT", e.message)

    /**
     * 이미 지난 차례의 쓰기가 도착해 튕겼다.
     *
     * **이 응답이 0 이 아니면 락이 뚫렸다는 증거다.** 락이 제 일을 했으면 지각 쓰기가 없어야 한다.
     * 그래서 warn 으로 남긴다 — 조용히 넘기면 3주차에서 볼 것이 사라진다.
     */
    @ExceptionHandler(StaleFenceException::class)
    fun handleStaleFence(e: StaleFenceException): ResponseEntity<Map<String, String?>> {
        log.warn("[펜싱] 지각 쓰기 차단 couponId={} 토큰={} 마지막={}", e.couponId, e.fence, e.lastFence)

        return problem(HttpStatus.CONFLICT, "STALE_FENCE", e.message)
    }

    /** 소진. `Coupon.issue` 의 `check` 가 던진다. */
    @ExceptionHandler(IllegalStateException::class)
    fun handleSoldOut(e: IllegalStateException) =
        problem(HttpStatus.CONFLICT, "SOLD_OUT", e.message)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException) =
        problem(HttpStatus.NOT_FOUND, "NOT_FOUND", e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException) =
        problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.message)

    private fun problem(status: HttpStatus, code: String, message: String?): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(status).body(mapOf("code" to code, "message" to message))
}
