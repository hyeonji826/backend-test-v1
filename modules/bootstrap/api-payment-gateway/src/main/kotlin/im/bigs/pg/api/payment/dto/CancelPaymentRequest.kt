package im.bigs.pg.api.payment.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/** 결제 취소 요청 DTO. cancelAmount 가 null 이면 전체 취소로 간주. */
data class CancelPaymentRequest(
    @field:DecimalMin("1")
    val cancelAmount: BigDecimal? = null,
    @field:Size(max = 255)
    val reason: String? = null,
)
