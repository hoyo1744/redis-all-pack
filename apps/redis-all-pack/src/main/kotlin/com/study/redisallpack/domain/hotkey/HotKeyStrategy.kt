package com.study.redisallpack.domain.hotkey

/**
 * 핫키 완화 전략. 락처럼 요청마다 갈아끼워 같은 조건에서 비교한다.
 *
 * 쿠폰이 하나면 키도 하나고, 클러스터가 3대여도 **그 슬롯을 가진 한 대만 탄다.**
 * 셋은 몰리는 걸 각각 다른 층에서 푼다.
 */
enum class HotKeyStrategy(val description: String) {

    /** 그대로. 한 노드에 전부 몰리는 걸 먼저 본다. */
    NONE("완화 없음 — 한 슬롯에 집중"),

    /** 앱 메모리에서 먼저 답한다. **Redis 왕복 자체가 사라진다.** */
    LOCAL_CACHE("로컬 캐시 — 왕복을 없앤다"),

    /**
     * [LOCAL_CACHE] 의 실무 버전. Caffeine 이 **동시 미스를 한 번의 로드로 합쳐**
     * 만료 순간 요청들이 Redis 로 우르르 몰려가는 캐시 스탬피드를 막는다.
     */
    CAFFEINE("카페인 — 로컬 캐시 + 스탬피드 방지"),

    /** 키를 N 개로 쪼갠다. 슬롯이 갈리므로 **여러 노드로 퍼진다.** */
    KEY_SHARDING("키 샤딩 — 슬롯을 흩는다"),

    /** 레플리카에서 읽는다. 마스터 부하를 나눈다. ⚠️ 복제 지연만큼 낡은 값을 본다. */
    REPLICA_READ("레플리카 읽기 — 마스터를 비운다"),
    ;

    companion object {
        fun from(raw: String?): HotKeyStrategy =
            raw?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } } ?: NONE
    }
}
