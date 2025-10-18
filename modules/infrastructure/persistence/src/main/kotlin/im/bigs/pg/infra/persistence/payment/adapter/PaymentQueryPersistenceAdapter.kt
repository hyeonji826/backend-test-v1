package im.bigs.pg.infra.persistence.payment.adapter

import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.infra.persistence.payment.entity.PaymentEntity
import im.bigs.pg.infra.persistence.payment.repository.PaymentJpaRepository
import java.math.BigDecimal
import java.time.Instant
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * Adapter providing page/summary queries as described in the issue.
 * This is a compatibility component that directly uses PaymentJpaRepository.
 */
@Component
class PaymentQueryPersistenceAdapter(
    private val repo: PaymentJpaRepository,
) {

    fun pageBy(
        partnerId: Long?,
        status: String?,
        from: Instant?,
        to: Instant?,
        cursorCreatedAt: Instant?,
        cursorId: Long?,
        pageable: Pageable,
    ): List<Payment> {
        val list = repo.pageBy(
            partnerId = partnerId,
            status = status,
            fromAt = from,
            toAt = to,
            cursorCreatedAt = cursorCreatedAt,
            cursorId = cursorId,
            org = pageable,
        )
        return list.map { it.toDomain() }
    }

    data class PaymentSummary(
        val count: Long,
        val totalAmount: BigDecimal,
        val totalNetAmount: BigDecimal,
    )

    fun summary(
        partnerId: Long?,
        status: String?,
        from: Instant?,
        to: Instant?,
    ): PaymentSummary {
        val row = repo.summary(
            partnerId = partnerId,
            status = status,
            fromAt = from,
            toAt = to,
        ).firstOrNull()

        if (row == null) {
            return PaymentSummary(0, BigDecimal.ZERO, BigDecimal.ZERO)
        }

        val count = (row[0] as Number).toLong()
        val totalAmount = row[1] as BigDecimal
        val totalNetAmount = row[2] as BigDecimal
        return PaymentSummary(count, totalAmount, totalNetAmount)
    }

    // Local mapping copied from PaymentPersistenceAdapter to avoid dependency cycle
    private fun PaymentEntity.toDomain(): Payment =
        Payment(
            id = this.id,
            partnerId = this.partnerId,
            amount = this.amount,
            appliedFeeRate = this.appliedFeeRate,
            feeAmount = this.feeAmount,
            netAmount = this.netAmount,
            cardBin = this.cardBin,
            cardLast4 = this.cardLast4,
            approvalCode = this.approvalCode,
            approvedAt = java.time.LocalDateTime.ofInstant(this.approvedAt, java.time.ZoneOffset.UTC),
            status = PaymentStatus.valueOf(this.status),
            createdAt = java.time.LocalDateTime.ofInstant(this.createdAt, java.time.ZoneOffset.UTC),
            updatedAt = java.time.LocalDateTime.ofInstant(this.updatedAt, java.time.ZoneOffset.UTC),
        )
}
