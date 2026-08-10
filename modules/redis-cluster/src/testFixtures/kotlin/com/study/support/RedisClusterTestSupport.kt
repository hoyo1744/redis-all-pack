package com.study.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * 로컬에 띄운 클러스터에 붙는다. Testcontainers 를 안 쓰는 이유는
 * 컨테이너 내부 IP 로는 cluster-announce 가 호스트 클라이언트에게 안 통하기 때문이다.
 *
 *   docker compose -f docker/redis-cluster-compose.yml up -d
 */
abstract class RedisClusterTestSupport {

    @Autowired protected lateinit var redisTemplate: StringRedisTemplate

    /** 테스트 키는 `test:` 로 시작한다. */
    protected fun deleteKeys(vararg keys: String) = keys.forEach { redisTemplate.delete(it) }
}
