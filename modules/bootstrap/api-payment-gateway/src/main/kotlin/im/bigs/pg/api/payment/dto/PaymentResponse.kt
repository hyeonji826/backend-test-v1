package im.bigs.pg.api.payment.dto

import im.bigs.pg.domain.payment.Payment
import java.math.BigDecimal

data class PaymentResponse(
    val id: Long?,
    val partnerId: Long,
    val amount: BigDecimal,
    val appliedFeeRate: BigDecimal,
    val feeAmount: BigDecimal,
    val netAmount: BigDecimal,
    val cardLast4: String?,
    val approvalCode: String?,
    val approvedAt: String?,
    val status: String,
    val createdAt: String?,
) {
    companion object {
        fun from(p: Payment) =
            PaymentResponse(
                id = p.id,
                partnerId = p.partnerId,
                amount = p.amount,
                appliedFeeRate = p.appliedFeeRate,
                feeAmount = p.feeAmount,
                netAmount = p.netAmount,
                cardLast4 = p.cardLast4,
                approvalCode = p.approvalCode,
                approvedAt = java.time.format.DateTimeFormatter.ISO_INSTANT.format(p.approvedAt.toInstant(java.time.ZoneOffset.UTC)),
                status = p.status.name,
                createdAt = java.time.format.DateTimeFormatter.ISO_INSTANT.format(p.createdAt.toInstant(java.time.ZoneOffset.UTC)),
            )
    }
}
