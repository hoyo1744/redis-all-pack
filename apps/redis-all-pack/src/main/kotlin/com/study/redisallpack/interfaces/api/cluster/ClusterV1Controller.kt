package com.study.redisallpack.interfaces.api.cluster

import com.study.redisallpack.application.cluster.ClusterFacade
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/cluster")
class ClusterV1Controller(
    private val clusterFacade: ClusterFacade,
) {

    /**
     * 멀티키 연산이 슬롯에 어떻게 걸리는지 보는 실험 지점. 결과가 서버 로그에도 남는다.
     *
     * ```
     * ?ids=A,B              해시태그 없음 → 슬롯이 갈림 → CROSSSLOT
     * ?ids=A,B&tag=true     해시태그 있음 → 한 슬롯   → 성공
     * ```
     */
    @GetMapping("/mget")
    fun mget(
        @RequestParam ids: List<String>,
        @RequestParam(name = "tag", required = false, defaultValue = "false") tag: Boolean,
    ) = clusterFacade.multiGet(ids, tag)

    /**
     * **클라이언트 셋이 같은 요청을 어떻게 처리하는지** 나란히 본다.
     *
     * ```
     * ?ids=A,B              해시태그 없음 → 슬롯이 갈림 → 쪼개주는 클라이언트만 성공
     * ?ids=A,B&tag=true     해시태그 있음 → 한 슬롯    → 전부 성공
     * ```
     */
    @GetMapping("/mget/compare")
    fun compare(
        @RequestParam ids: List<String>,
        @RequestParam(name = "tag", required = false, defaultValue = "false") tag: Boolean,
    ) = clusterFacade.compareClients(ids, tag)
}
