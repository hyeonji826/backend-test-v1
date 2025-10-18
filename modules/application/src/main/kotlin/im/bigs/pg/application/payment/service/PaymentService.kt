package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.CancelCommand
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgCancelRequest
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.FeeCalculator
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 결제 생성/취소 유스케이스 구현체.
 */
@Service
class PaymentService(
    private val partnerRepository: PartnerOutPort,
    private val feePolicyRepository: FeePolicyOutPort,
    private val paymentRepository: PaymentOutPort,
    private val pgClients: List<PgClientOutPort>,
) : PaymentUseCase {
    override fun pay(command: PaymentCommand): Payment {
        val partner =
            partnerRepository.findById(command.partnerId)
                ?: throw im.bigs.pg.common.NotFoundException("Partner not found: ${command.partnerId}")
        require(partner.active) { "Partner is inactive: ${partner.id}" }

        val now = LocalDateTime.now()

        val policy =
            feePolicyRepository.findEffectivePolicy(partner.id, now)
                ?: throw IllegalStateException("No effective fee policy for partner ${partner.id} at $now")

        val calc = FeeCalculator.calculate(command.amount, policy)

        val pgClient =
            pgClients.firstOrNull { it.supports(partner.id) }
                ?: throw IllegalStateException("No PG client supports partner ${partner.id}")

        val approve =
            pgClient.approve(
                PgApproveRequest(
                    partnerId = partner.id,
                    amount = command.amount,
                    cardBin = command.cardBin,
                    cardLast4 = command.cardLast4,
                    productName = command.productName,
                ),
            )

        val payment =
            Payment(
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

    override fun cancel(command: CancelCommand): Payment {
        val existing = paymentRepository.findById(command.paymentId)
            ?: throw im.bigs.pg.common.NotFoundException("Payment not found: ${command.paymentId}")
        if (existing.status == PaymentStatus.CANCELED) {
            return existing
        }
        // Validate cancel amount if provided
        if (command.cancelAmount != null) {
            if (command.cancelAmount.signum() <= 0) {
                throw im.bigs.pg.common.UnprocessableException("cancelAmount must be > 0")
            }
            if (command.cancelAmount > existing.amount) {
                throw im.bigs.pg.common.UnprocessableException("cancelAmount exceeds approved amount")
            }
        }
        val pgClient = pgClients.firstOrNull { it.supports(existing.partnerId) }
            ?: throw IllegalStateException("No PG client supports partner ${existing.partnerId}")
        // Call PG cancel (errors tolerated in demo)
        try {
            pgClient.cancel(
                PgCancelRequest(
                    partnerId = existing.partnerId,
                    paymentId = existing.id!!,
                    cancelAmount = command.cancelAmount,
                    reason = command.reason,
                ),
            )
        } catch (_: Exception) {
            // ignore in demo
        }
        val now = LocalDateTime.now()
        val updated = existing.copy(status = PaymentStatus.CANCELED, updatedAt = now)
        return paymentRepository.save(updated)
    }
}
