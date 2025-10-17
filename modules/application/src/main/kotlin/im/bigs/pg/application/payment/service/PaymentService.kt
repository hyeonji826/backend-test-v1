package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.FeeCalculator
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 결제 생성 유스케이스 구현체.
 * - 입력(REST 등) → 도메인/외부PG/영속성 포트를 순차적으로 호출하는 흐름을 담당합니다.
 * - 수수료 정책 조회 및 적용(계산)은 도메인 유틸리티를 통해 수행합니다.
 */
@Service
class PaymentService(
    private val partnerRepository: PartnerOutPort,
    private val feePolicyRepository: FeePolicyOutPort,
    private val paymentRepository: PaymentOutPort,
    private val pgClients: List<PgClientOutPort>,
) : PaymentUseCase {
    /**
     * 결제 승인/수수료 계산/저장을 순차적으로 수행합니다.
     */
    override fun pay(command: PaymentCommand): Payment {
        val partner = partnerRepository.findById(command.partnerId)
            ?: throw IllegalArgumentException("Partner not found: ${command.partnerId}")
        require(partner.active) { "Partner is inactive: ${partner.id}" }

        val now = LocalDateTime.now()

        val policy = feePolicyRepository.findEffectivePolicy(partner.id, now)
            ?: throw IllegalStateException("No effective fee policy for partner ${partner.id} at $now")

        val calc = FeeCalculator.calculate(command.amount, policy)

        val pgClient = pgClients.firstOrNull { it.supports(partner.id) }
            ?: throw IllegalStateException("No PG client supports partner ${partner.id}")

        val approve = pgClient.approve(
            PgApproveRequest(
                partnerId = partner.id,
                amount = command.amount,
                cardBin = command.cardBin,
                cardLast4 = command.cardLast4,
                productName = command.productName,
            ),
        )

        val payment = Payment(
            id = null,
            partnerId = partner.id,
            amount = command.amount,
            appliedFeeRate = calc.appliedFeeRate,
            feeAmount = calc.feeAmount,
            netAmount = calc.netAmount,
            cardBin = command.cardBin?.takeIf { it.isNotBlank() },
            cardLast4 = command.cardLast4,
            approvalCode = approve.approvalCode,
            approvedAt = approve.approvedAt,
            status = approve.status,
            createdAt = now,
            updatedAt = now,
        )

        return paymentRepository.save(payment)
    }
}
