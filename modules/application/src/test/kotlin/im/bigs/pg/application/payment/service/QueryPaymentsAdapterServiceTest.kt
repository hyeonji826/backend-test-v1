package im.bigs.pg.application.payment.service

import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.`in`.QueryPaymentsUseCase
import im.bigs.pg.application.payment.port.`in`.QueryResult
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.domain.payment.PaymentSummary
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryPaymentsAdapterServiceTest {

    private val useCase: QueryPaymentsUseCase = mockk()
    private val adapter = QueryPaymentsAdapterService(useCase)

    @Test
    fun `should map Query to QueryFilter and map back to PageResult`() {
        // given
        val nowInstant = Instant.parse("2024-01-02T03:04:05Z")
        val nowLdt = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC)

        val payment = Payment(
            id = 1L,
            partnerId = 99L,
            amount = BigDecimal("1000"),
            appliedFeeRate = BigDecimal("0.0300"),
            feeAmount = BigDecimal("30"),
            netAmount = BigDecimal("970"),
            cardBin = null,
            cardLast4 = "4242",
            approvalCode = "APPROVAL",
            approvedAt = nowLdt,
            status = PaymentStatus.APPROVED,
            createdAt = nowLdt,
            updatedAt = nowLdt,
        )
        val summary = PaymentSummary(
            count = 1L,
            totalAmount = BigDecimal("1000"),
            totalNetAmount = BigDecimal("970"),
        )
        val expectedResult = QueryResult(
            items = listOf(payment),
            summary = summary,
            nextCursor = "cursor-token",
            hasNext = false,
        )

        every { useCase.query(any<QueryFilter>()) } returns expectedResult

        val q = Query(
            partnerId = 99L,
            status = "APPROVED",
            from = nowInstant,
            to = nowInstant.plusSeconds(60),
            limit = 10,
            cursor = null,
        )

        // when
        val res = adapter.execute(q)

        // then
        assertEquals(1, res.items.size)
        assertEquals(summary, res.summary)
        assertEquals("cursor-token", res.nextCursor)
        assertEquals(false, res.hasNext)
    }
}
