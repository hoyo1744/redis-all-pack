package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.FenceGenerator
import jakarta.annotation.PreDestroy
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * 순차 znode 로 펜싱 토큰을 발급한다.
 *
 * 모든 쓰기가 과반 fsync 후에야 커밋되고, 리더가 바뀌어도 zxid 의 epoch 가 올라가
 * 전체 번호는 계속 증가한다. **되감기지 않는다.**
 *
 * ⚠️ 무조건은 아니다 — 부모 znode 를 지웠다 만들면 0 부터 시작하고, `forceSync=no` 면 보장이 깨진다.
 */
@Component
@Qualifier(FENCE_DELEGATE)
@ConditionalOnProperty(name = ["coupon.fence.source"], havingValue = "zookeeper")
class ZookeeperFenceGenerator(
    @Value("\${coupon.fence.zookeeper.connect-string:localhost:2181,localhost:2182,localhost:2183}")
    connectString: String,
    @Value("\${coupon.fence.zookeeper.base-path:/fence}") private val basePath: String,
) : FenceGenerator {

    private val log = LoggerFactory.getLogger(javaClass)

    override val source = "zookeeper-sequential"

    private val client: CuratorFramework = CuratorFrameworkFactory.builder()
        .connectString(connectString)
        .retryPolicy(ExponentialBackoffRetry(100, 3))
        .sessionTimeoutMs(10_000)
        .connectionTimeoutMs(3_000)
        .build()
        .apply { start() }

    @PreDestroy
    fun shutdown() = client.close()

    override fun next(key: String): Long {
        val created = client.create()
            .creatingParentsIfNeeded()
            .withMode(CreateMode.PERSISTENT_SEQUENTIAL)
            .forPath("$basePath/${sanitize(key)}/$SEQ_PREFIX")

        // 번호만 쓰고 지운다(안 지우면 무한 축적). cversion 은 delete 로도 올라가 단조성은 유지된다.
        // 동기로 지우면 왕복이 두 배가 되므로 백그라운드로 던진다.
        client.delete().guaranteed().inBackground().forPath(created)

        // 순차 znode 는 0 부터 시작한다. lastFence 초기값 0 과 충돌하므로 +1 ([FenceGenerator] 계약).
        return created.substringAfterLast(SEQ_PREFIX).toLong() + 1
    }

    /** znode 경로에 `/` 를 못 넣는다. 락 키가 `lock:{coupon:BF}` 라 그대로는 못 쓴다. */
    private fun sanitize(key: String) = key.replace(NON_PATH_SAFE, "_")

    private companion object {
        const val SEQ_PREFIX = "seq-"
        val NON_PATH_SAFE = Regex("[^A-Za-z0-9._-]")
    }
}
