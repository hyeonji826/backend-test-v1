package im.bigs.pg.application.payment.port.`in`

import java.math.BigDecimal

data class CancelCommand(
    val paymentId: Long,
    val cancelAmount: BigDecimal?,
    val reason: String?,
)
