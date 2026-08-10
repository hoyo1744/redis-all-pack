package com.study.redisallpack.support

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/cluster")
class ClusterController(
    private val inspector: ClusterInspector,
) {

    @GetMapping("/topology")
    fun topology() = inspector.topology()

    @GetMapping("/locate")
    fun locate(@RequestParam key: String) = inspector.locate(key)

    @GetMapping("/distribution")
    fun distribution(
        @RequestParam prefix: String,
        @RequestParam(defaultValue = "1000") count: Int,
    ) = inspector.distribution((1..count).map { "$prefix$it" })
}
