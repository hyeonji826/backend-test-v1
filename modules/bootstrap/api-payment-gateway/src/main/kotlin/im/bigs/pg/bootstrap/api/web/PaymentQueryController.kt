package im.bigs.pg.bootstrap.api.web

import im.bigs.pg.application.payment.service.Query
import im.bigs.pg.application.payment.service.QueryPaymentsAdapterService
import im.bigs.pg.domain.payment.Payment
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class PaymentItemDto(
    val id: Long?,
    val partnerId: Long,
    val amount: java.math.BigDecimal,
    val appliedFeeRate: java.math.BigDecimal,
    val feeAmount: java.math.BigDecimal,
    val netAmount: java.math.BigDecimal,
    val cardBin: String?,
    val cardLast4: String?,
    val approvalCode: String,
    val approvedAt: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

data class PaymentsResponse(
    val items: List<PaymentItemDto>,
    val summary: SummaryDto,
    val nextCursor: String?,
    val hasNext: Boolean,
)

data class SummaryDto(
    val count: Long,
    val totalAmount: String,
    val totalNetAmount: String,
)

@Validated
@RestController
@RequestMapping("/api/v1/payments")
class PaymentQueryController(
    private val svc: QueryPaymentsAdapterService,
) {
    @GetMapping
    fun getPayments(
        @RequestParam(required = false) partnerId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): ResponseEntity<PaymentsResponse> {
        val r =
            svc.execute(
                Query(
                    partnerId = partnerId,
                    status = status,
                    from = from,
                    to = to,
                    limit = limit,
                    cursor = cursor,
                ),
            )
        val iso = java.time.format.DateTimeFormatter.ISO_INSTANT
        val itemsDto = r.items.map { p ->
            PaymentItemDto(
                id = p.id,
                partnerId = p.partnerId,
                amount = p.amount,
                appliedFeeRate = p.appliedFeeRate,
                feeAmount = p.feeAmount,
                netAmount = p.netAmount,
                cardBin = p.cardBin,
                cardLast4 = p.cardLast4,
                approvalCode = p.approvalCode,
                approvedAt = iso.format(p.approvedAt.toInstant(java.time.ZoneOffset.UTC)),
                status = p.status.name,
                createdAt = iso.format(p.createdAt.toInstant(java.time.ZoneOffset.UTC)),
                updatedAt = iso.format(p.updatedAt.toInstant(java.time.ZoneOffset.UTC)),
            )
        }
        val body =
            PaymentsResponse(
                items = itemsDto,
                summary =
                    SummaryDto(
                        count = r.summary.count,
                        totalAmount = r.summary.totalAmount.toPlainString(),
                        totalNetAmount = r.summary.totalNetAmount.toPlainString(),
                    ),
                nextCursor = r.nextCursor,
                hasNext = r.hasNext,
            )
        return ResponseEntity.ok(body)
    }
}
