package im.bigs.pg.infra.persistence.payment.repository

import im.bigs.pg.infra.persistence.payment.entity.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** 결제 이력 조회용 JPA 리포지토리. */
interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    /**
     * 커서(createdAt, id) 기반 내림차순 페이지 쿼리.
     * Pageable 의 page-size = limit+1 로 요청하여 다음 페이지 존재 여부를 판별합니다.
     */
    @Query(
        """
        select p from PaymentEntity p
        where (:partnerId is null or p.partnerId = :partnerId)
          and (:status is null or p.status = :status)
          and (:fromAt is null or p.createdAt >= :fromAt)
          and (:toAt is null or p.createdAt < :toAt)
          and (
                (:cursorCreatedAt is null and :cursorId is null)
             or (p.createdAt < :cursorCreatedAt)
             or (p.createdAt = :cursorCreatedAt and p.id < :cursorId)
          )
        order by p.createdAt desc, p.id desc
        """,
    )
    fun pageBy(
        @Param("partnerId") partnerId: Long?,
        @Param("status") status: String?,
        @Param("fromAt") fromAt: Instant?,
        @Param("toAt") toAt: Instant?,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant?,
        @Param("cursorId") cursorId: Long?,
        org: org.springframework.data.domain.Pageable,
    ): List<PaymentEntity>

    /** 통계 합계/건수 조회. */
    @Query(
        """
        select count(p) as cnt, coalesce(sum(p.amount),0) as totalAmount, coalesce(sum(p.netAmount),0) as totalNet
        from PaymentEntity p
        where (:partnerId is null or p.partnerId = :partnerId)
          and (:status is null or p.status = :status)
          and (:fromAt is null or p.createdAt >= :fromAt)
          and (:toAt is null or p.createdAt < :toAt)
        """,
    )
    fun summary(
        @Param("partnerId") partnerId: Long?,
        @Param("status") status: String?,
        @Param("fromAt") fromAt: Instant?,
        @Param("toAt") toAt: Instant?,
    ): List<Array<Any>>
}
