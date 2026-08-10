package com.study.redisallpack

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 패키지가 `com.study` 아래여야 `modules/redis-cluster` 의 `com.study.config.redis` 가 스캔된다.
 */
@SpringBootApplication(scanBasePackages = ["com.study"])
class RedisAllPackApplication

fun main(args: Array<String>) {
    runApplication<RedisAllPackApplication>(*args)
}
