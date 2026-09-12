package com.study.redisallpack.domain.lock

import com.study.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 펜싱 토큰 카운터를 DB 에 둔다. **Redis `INCR` 의 되감김 문제를 없애려는 것이다.**
 *
 * `token` 이 곧 버전 노릇을 한다. 단조 증가하므로 따로 `version` 컬럼을 둘 이유가 없다 —
 * *"내가 읽은 값이 아직 그대로인가"* 를 `token` 자체로 물으면 된다.
 *
 * ⚠️ JPA `@Version` 을 쓰지 않는다. 그걸 붙이면 **엔티티에 대한 모든 쓰기**가 버전 검사를 받는데,
 * 이 프로젝트는 `WriteMode.PLAIN` 으로 lost update 를 **일부러 재현**해야 해서
 * 기준선이 오염된다. 낙관적 락의 메커니즘(읽은 값과 비교 후 조건부 쓰기)은 동일하되,
 * 검사를 필요한 곳에서만 명시적으로 한다.
 */
@Entity
@Table(name = "fence_sequence")
class FenceSequence protected constructor(
    fenceKey: String,
    token: Long,
) : BaseEntity() {

    @Column(name = "fence_key", nullable = false, unique = true, length = 200)
    var fenceKey: String = fenceKey
        protected set

    @Column(name = "token", nullable = false)
    var token: Long = token
        protected set

    companion object {
        fun of(fenceKey: String) = FenceSequence(fenceKey, token = 0)
    }
}
