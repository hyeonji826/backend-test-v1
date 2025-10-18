package im.bigs.pg.api.payment.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreatePaymentRequest(
    @field:NotNull val partnerId: Long,
    @field:NotNull @field:DecimalMin("1") val amount: BigDecimal,
    @field:Pattern(regexp = "^[0-9]{4,6}$") val cardBin: String? = null,
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{4}$") val cardLast4: String,
    @field:NotBlank @field:Size(max = 255) val productName: String,
)
