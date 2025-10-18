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
        fun from(p: Payment) = PaymentResponse(
            id = p.id,
            partnerId = p.partnerId,
            amount = p.amount,
            appliedFeeRate = p.appliedFeeRate,
            feeAmount = p.feeAmount,
            netAmount = p.netAmount,
            cardLast4 = p.cardLast4,
            approvalCode = p.approvalCode,
            approvedAt = p.approvedAt.toString(),
            status = p.status.name,
            createdAt = p.createdAt.toString(),
        )
    }
}
