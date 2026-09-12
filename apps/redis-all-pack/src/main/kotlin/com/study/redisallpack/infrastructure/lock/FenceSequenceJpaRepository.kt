package com.study.redisallpack.infrastructure.lock

import com.study.redisallpack.domain.lock.FenceSequence
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface FenceSequenceJpaRepository : JpaRepository<FenceSequence, Long> {

    fun findByFenceKey(fenceKey: String): FenceSequence?

    /**
     * **낙관적 락의 핵심 한 줄.** 내가 읽은 값이 아직 그대로일 때만 올린다.
     *
     * `token` 이 버전 노릇을 겸한다 — 단조 증가하므로 별도 `version` 컬럼이 필요 없다.
     *
     * @return 1 이면 내가 가져갔다. **0 이면 내가 읽은 뒤 남이 먼저 올렸다** — 다시 읽고 재시도해야 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
        """
        UPDATE FenceSequence f
           SET f.token = f.token + 1
         WHERE f.fenceKey = :fenceKey
           AND f.token = :expected
        """,
    )
    fun bumpIfUnchanged(@Param("fenceKey") fenceKey: String, @Param("expected") expected: Long): Int
}
