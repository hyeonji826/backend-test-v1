package im.bigs.pg.api.config

import im.bigs.pg.infra.persistence.partner.entity.FeePolicyEntity
import im.bigs.pg.infra.persistence.partner.entity.PartnerEntity
import im.bigs.pg.infra.persistence.partner.repository.FeePolicyJpaRepository
import im.bigs.pg.infra.persistence.partner.repository.PartnerJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.time.Instant

/**
 * 로컬/데모 환경에서 빠른 실행을 위한 간단한 시드 데이터.
 * - 운영 환경에서는 제거하거나 마이그레이션 도구로 대체합니다.
 */
@Configuration
class DataInitializer {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun seed(
        partnerRepo: PartnerJpaRepository,
        feeRepo: FeePolicyJpaRepository,
    ) = CommandLineRunner {
        if (partnerRepo.count() == 0L) {
            val p1 = partnerRepo.save(PartnerEntity(code = "MOCK1", name = "Mock Partner 1", active = true))
            val p2 = partnerRepo.save(PartnerEntity(code = "TESTPAY1", name = "TestPay Partner 1", active = true))
            feeRepo.save(
                FeePolicyEntity(
                    partnerId = p1.id!!,
                    effectiveFrom = Instant.parse("2020-01-01T00:00:00Z"),
                    percentage = BigDecimal("0.0235"),
                    fixedFee = BigDecimal.ZERO,
                ),
            )
            feeRepo.save(
                FeePolicyEntity(
                    partnerId = p2.id!!,
                    effectiveFrom = Instant.parse("2020-01-01T00:00:00Z"),
                    percentage = BigDecimal("0.0300"),
                    fixedFee = BigDecimal("100"),
                ),
            )
            log.info("Seeded partners: {} and {}", p1.id, p2.id)
        }
    }
}
