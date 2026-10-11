package com.study.redisallpack.domain.layout

/**
 * 한 묶음(그룹)의 아이템 N 개를 Redis 에 **어떤 모양으로** 넣을 것인가.
 *
 * ```
 * STRING_KEYS   layout:str:{그룹}:{아이템}  → 값        키 N 개
 * HASH          layout:hash:{그룹}          → 필드 N 개  키 1 개
 * JSON_STRING   layout:json:{그룹}          → JSON 통째  키 1 개
 * ```
 */
enum class ItemLayout(val description: String) {

    /** 아이템마다 키 하나. 키마다 dictEntry·robj·키 SDS 가 따로 붙는다. */
    STRING_KEYS("키 N 개 — 아이템마다 String 키"),

    /**
     * 키 하나에 필드 N 개. 작으면 **listpack**(연속 메모리 한 덩어리)으로 인코딩된다.
     * ⚠️ 필드 수나 값 길이가 `hash-max-listpack-*` 를 넘으면 hashtable 로 바뀌고 이득이 줄어든다.
     */
    HASH("키 1 개 — Hash 필드 N 개"),

    /** 키 하나에 JSON 통째. 메모리는 작지만 아이템 하나를 바꾸려 해도 전체를 읽고 써야 한다. */
    JSON_STRING("키 1 개 — JSON 문자열 통째"),

    /**
     * JSON_STRING 과 데이터 모델은 같고 **포맷만 Smile(바이너리 JSON)**. 따옴표·콜론·쉼표가
     * 토큰 1 바이트로 줄어든다. 통째로 읽고 쓰는 성질은 JSON 과 똑같다.
     */
    SMILE_STRING("키 1 개 — Smile(바이너리 JSON) 통째"),
}
