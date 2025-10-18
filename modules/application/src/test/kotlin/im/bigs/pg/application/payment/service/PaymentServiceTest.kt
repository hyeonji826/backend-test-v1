package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgCancelRequest
import im.bigs.pg.application.pg.port.out.PgCancelResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.partner.Partner
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentServiceTest {
    private val partnerRepo: PartnerOutPort = mockk()
    private val feeRepo: FeePolicyOutPort = mockk()
    private val paymentRepo: PaymentOutPort = mockk()

    private val pgClient: PgClientOutPort =
        object : PgClientOutPort {
            override fun supports(partnerId: Long): Boolean = true

            override fun approve(request: PgApproveRequest): PgApproveResult =
                PgApproveResult(
                    approvalCode = "APPROVAL-123",
                    approvedAt = LocalDateTime.of(2024, 1, 1, 0, 0),
                    status = PaymentStatus.APPROVED,
                )

            override fun cancel(request: PgCancelRequest): PgCancelResult =
                PgCancelResult(
                    canceledAt = LocalDateTime.of(2024, 1, 1, 0, 10),
                    status = PaymentStatus.CANCELED,
                )
        }

    @Test
    @DisplayName("Should apply fee policy and persist when creating a payment")
    fun apply_fee_policy_and_persist_on_payment_creation() {
        val service =
            PaymentService(
                partnerRepository = partnerRepo,
                feePolicyRepository = feeRepo,
                paymentRepository = paymentRepo,
                pgClients = listOf(pgClient),
            )

        every { partnerRepo.findById(1L) } returns
            Partner(
                id = 1L,
                code = "TEST",
                name = "Test",
                active = true,
            )

        every { feeRepo.findEffectivePolicy(1L, any()) } returns
            FeePolicy(
                id = 10L,
                partnerId = 1L,
                effectiveFrom =
                    LocalDateTime.ofInstant(
                        Instant.parse("2020-01-01T00:00:00Z"),
                        ZoneOffset.UTC,
                    ),
                percentage = BigDecimal("0.0300"),
                fixedFee = BigDecimal("100"),
            )

        val savedSlot = slot<Payment>()
        every { paymentRepo.save(capture(savedSlot)) } answers {
            savedSlot.captured.copy(id = 99L)
        }

        val cmd =
            PaymentCommand(
                partnerId = 1L,
                amount = BigDecimal("10000"),
                cardLast4 = "4242",
            )

        val res = service.pay(cmd)

        assertEquals(99L, res.id)
        assertEquals(BigDecimal("400"), res.feeAmount)
        assertEquals(BigDecimal("9600"), res.netAmount)
        assertEquals(PaymentStatus.APPROVED, res.status)
    }
}
