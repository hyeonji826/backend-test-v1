package im.bigs.pg.application.payment.service

import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.`in`.QueryPaymentsUseCase
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentSummary
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Compatibility adapter for the alternative Query/PageResult API described in the issue.
 * It delegates to the existing QueryPaymentsUseCase and performs simple type conversions.
 */

data class Query(
    val partnerId: Long?,
    val status: String?,
    val from: Instant?,
    val to: Instant?,
    val limit: Int,
    val cursor: String?,
)

data class PageResult(
    val items: List<Payment>,
    val summary: PaymentSummary,
    val nextCursor: String?,
    val hasNext: Boolean,
)

@Service
class QueryPaymentsAdapterService(
    private val queryPaymentsUseCase: QueryPaymentsUseCase,
) {
    fun execute(q: Query): PageResult {
        val res =
            queryPaymentsUseCase.query(
                QueryFilter(
                    partnerId = q.partnerId,
                    status = q.status,
                    from = q.from?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
                    to = q.to?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
                    cursor = q.cursor,
                    limit = q.limit,
                ),
            )
        return PageResult(
            items = res.items,
            summary = res.summary,
            nextCursor = res.nextCursor,
            hasNext = res.hasNext,
        )
    }
}
