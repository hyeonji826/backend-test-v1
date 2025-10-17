package im.bigs.pg.api.payment.dto

import jakarta.validation.constraints.Min
import java.math.BigDecimal

data class CreatePaymentRequest(
    val partnerId: Long,
    @field:Min(1)
    val amount: BigDecimal,
    val cardBin: String? = null,
    val cardLast4: String? = null,
    val productName: String? = null,
)
