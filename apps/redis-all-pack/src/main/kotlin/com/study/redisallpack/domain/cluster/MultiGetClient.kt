package com.study.redisallpack.domain.cluster

/**
 * 클라이언트 하나를 감싼다. 구현이 셋(Lettuce · Jedis · Redisson)이고
 * 같은 요청을 각자 방식으로 처리한다.
 */
interface MultiGetClient {

    /** 결과에 찍을 이름. */
    val name: String

    fun seed(keys: List<String>)

    /** @throws Exception 슬롯이 갈리는데 쪼개지 못하면 그대로 던진다. */
    fun multiGet(keys: List<String>): Map<String, String>
}
