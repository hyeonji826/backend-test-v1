package im.bigs.pg.infra.persistence.partner.adapter

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.infra.persistence.partner.repository.FeePolicyJpaRepository
import org.springframework.stereotype.Component
import java.time.ZoneOffset

/** 수수료 정책 조회 어댑터. */
@Component
class FeePolicyPersistenceAdapter(
    private val repo: FeePolicyJpaRepository,
) : FeePolicyOutPort {
    override fun findEffectivePolicy(partnerId: Long, at: java.time.LocalDateTime): FeePolicy? {
        val e = repo.findTopByPartnerIdAndEffectiveFromBeforeEqOrderByEffectiveFromDesc(partnerId, at).firstOrNull()
            ?: return null
        return FeePolicy(
            id = e.id,
            partnerId = e.partnerId,
            effectiveFrom = java.time.LocalDateTime.ofInstant(e.effectiveFrom, ZoneOffset.UTC),
            percentage = e.percentage,
            fixedFee = e.fixedFee,
        )
    }
}
