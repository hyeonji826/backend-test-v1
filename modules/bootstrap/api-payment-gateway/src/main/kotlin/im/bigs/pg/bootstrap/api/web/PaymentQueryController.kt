package im.bigs.pg.bootstrap.api.web

import im.bigs.pg.application.payment.service.Query
import im.bigs.pg.application.payment.service.QueryPaymentsAdapterService
import im.bigs.pg.domain.payment.Payment
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class PaymentsResponse(
    val items: List<Payment>,
    val summary: SummaryDto,
    val nextCursor: String?,
    val hasNext: Boolean
)

data class SummaryDto(
    val count: Long,
    val totalAmount: String,
    val totalNetAmount: String
)

@Validated
@RestController
@RequestMapping("/api/v1/payments")
class PaymentQueryController(
    private val svc: QueryPaymentsAdapterService
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
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int
    ): ResponseEntity<PaymentsResponse> {
        val r = svc.execute(
            Query(
                partnerId = partnerId,
                status = status,
                from = from,
                to = to,
                limit = limit,
                cursor = cursor
            )
        )
        val body = PaymentsResponse(
            items = r.items,
            summary = SummaryDto(
                count = r.summary.count,
                totalAmount = r.summary.totalAmount.toPlainString(),
                totalNetAmount = r.summary.totalNetAmount.toPlainString()
            ),
            nextCursor = r.nextCursor,
            hasNext = r.hasNext
        )
        return ResponseEntity.ok(body)
    }
}
