package im.bigs.pg.infra.persistence.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * DB용 결제 이력 엔티티.
 * - createdAt/Id 조합을 커서 정렬 키로 사용합니다.
 */
@Entity
@Table(name = "payment")
class PaymentEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "partner_id", nullable = false)
    var partnerId: Long,
    @Column(name = "amount", nullable = false, precision = 15, scale = 0)
    var amount: BigDecimal,
    @Column(name = "applied_fee_rate", nullable = false, precision = 10, scale = 6)
    var appliedFeeRate: BigDecimal,
    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 0)
    var feeAmount: BigDecimal,
    @Column(name = "net_amount", nullable = false, precision = 15, scale = 0)
    var netAmount: BigDecimal,
    @Column(name = "card_bin", length = 8)
    var cardBin: String? = null,
    @Column(name = "card_last4", length = 4)
    var cardLast4: String? = null,
    @Column(name = "approval_code", nullable = false, length = 32)
    var approvalCode: String,
    @Column(name = "approved_at", nullable = false)
    var approvedAt: Instant,
    @Column(name = "status", nullable = false, length = 20)
    var status: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
