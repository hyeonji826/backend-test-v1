package im.bigs.pg.infra.persistence.partner.repository

import im.bigs.pg.infra.persistence.partner.entity.FeePolicyEntity
import java.time.Instant
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 수수료 정책 조회용 JPA 리포지토리. */
interface FeePolicyJpaRepository : JpaRepository<FeePolicyEntity, Long> {
    /** 지정 시점 이전/동일한 정책 중 가장 최근 것을 반환합니다. (파생 쿼리) */
    fun findTop1ByPartnerIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        partnerId: Long,
        at: Instant,
    ): FeePolicyEntity?

    /**
     * 지정 시점(at) 이전/동일한 정책을 최신순으로 모두 조회합니다. (명시적 JPQL)
     * 이 메서드는 과제 사양에 따라 LocalDateTime 파라미터와 @Query를 사용합니다.
     */
    @Query(
        """
        SELECT f FROM FeePolicyEntity f
        WHERE f.partnerId = :partnerId
          AND f.effectiveFrom <= :at
        ORDER BY f.effectiveFrom DESC, f.id DESC
        """
    )
    fun findTopByPartnerIdAndEffectiveFromBeforeEqOrderByEffectiveFromDesc(
        @Param("partnerId") partnerId: Long,
        @Param("at") at: LocalDateTime,
    ): List<FeePolicyEntity>
}
