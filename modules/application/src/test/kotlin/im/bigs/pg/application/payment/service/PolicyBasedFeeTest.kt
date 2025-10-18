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
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyBasedFeeTest {
    @Test
    @DisplayName("정책(3%+100) 기반으로 fee/net 계산하고 승인 후 저장한다")
    fun policy_based_fee_and_persist() {
        val partnerRepo: PartnerOutPort = mockk()
        val feeRepo: FeePolicyOutPort = mockk()
        val payRepo: PaymentOutPort = mockk()

        val pgClient =
            object : PgClientOutPort {
                override fun supports(partnerId: Long) = true

                override fun approve(request: PgApproveRequest) =
                    PgApproveResult(
                        approvalCode = "OK123",
                        approvedAt = LocalDateTime.of(2025, 1, 1, 0, 0),
                        status = PaymentStatus.APPROVED,
                    )

                override fun cancel(request: PgCancelRequest) =
                    PgCancelResult(
                        canceledAt = LocalDateTime.of(2025, 1, 1, 0, 10),
                        status = PaymentStatus.CANCELED,
                    )
            }

        every { partnerRepo.findById(1L) } returns Partner(1L, "T", "Test", true)
        every { feeRepo.findEffectivePolicy(1L, any()) } returns
            FeePolicy(
                id = 10L, partnerId = 1L,
                effectiveFrom = LocalDateTime.of(2020, 1, 1, 0, 0),
                percentage = BigDecimal("0.0300"),
                fixedFee = BigDecimal("100"),
            )

        val slot = slot<Payment>()
        every { payRepo.save(capture(slot)) } answers { slot.captured.copy(id = 99L) }

        val service = PaymentService(partnerRepo, feeRepo, payRepo, listOf(pgClient))

        val res =
            service.pay(
                PaymentCommand(
                    partnerId = 1L,
                    amount = BigDecimal("10000"),
                    cardLast4 = "4242",
                    cardBin = "123456",
                    productName = "샘플",
                ),
            )

        assertEquals(99L, res.id)
        assertEquals(BigDecimal("0.0300"), res.appliedFeeRate)
        assertEquals(BigDecimal("400"), res.feeAmount) // 300 + 100 → 400
        assertEquals(BigDecimal("9600"), res.netAmount)
        assertEquals(PaymentStatus.APPROVED, res.status)
    }
}
