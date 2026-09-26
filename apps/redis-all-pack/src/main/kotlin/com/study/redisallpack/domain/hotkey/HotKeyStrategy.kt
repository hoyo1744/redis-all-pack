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

    /**
     * 순진한 대안 ①. 짧은 TTL 에 난수를 더해 만료 시각을 흩는다.
     * ⚠️ **단일 핫키엔 무력하다** — 키가 하나면 만료 시각만 옮길 뿐 그 순간 떼몰림은 그대로다.
     */
    JITTER("TTL 지터 — 만료 시각 흩기 (단일 키엔 무력)"),

    /**
     * 순진한 대안 ②. 만료되면 `SET NX` 로 락을 잡은 하나만 재계산하고 나머지는 대기한다.
     * ⚠️ 원본 호출은 1 회로 눌리지만 **락 키가 새 병목**이 되고 못 잡은 요청은 지연된다.
     */
    LOCK("분산 락 — 하나만 재계산, 나머지 대기"),

    /**
     * soft TTL(stale-while-revalidate) — **로컬** 버전. Caffeine `refreshAfterWrite` 로
     * 인스턴스 "안" 스탬피드만 막는다(로컬 → Redis).
     */
    SOFT_TTL("로컬 소프트 TTL — Caffeine refreshAfterWrite"),

    /**
     * soft TTL — **Redis(전역) 버전**. `값|soft|hard` 를 Redis 에 저장하고, 소프트 만료
     * 구간이면 낡은 값 즉시 반환 + `SET NX` 로 **하나만** DB 갱신. PER 과 같은 Redis→DB 층에서
     * 붙는 직접 라이벌이다. ⚠️ 전역 1 회로 누르려면 락이 필요하고, 갱신 중 낡은 값을 낸다.
     */
    REDIS_SOFT_TTL("전역 소프트 TTL — Redis 레이어 stale-while-revalidate"),

    /**
     * 확률적 조기 재계산(PER). 만료가 가까울수록 높은 확률로 **미리** 갱신해,
     * 만료 순간 모든 요청이 동시에 미스를 내는 **캐시 스탬피드를 락 없이** 없앤다.
     */
    PER("확률적 조기 재계산 — 락 없이 스탬피드 방지"),
    ;

    companion object {
        fun from(raw: String?): HotKeyStrategy =
            raw?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } } ?: NONE
    }
}
