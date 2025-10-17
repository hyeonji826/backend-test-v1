package im.bigs.pg.infra.persistence.partner.adapter

import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.domain.partner.Partner
import im.bigs.pg.infra.persistence.partner.repository.PartnerJpaRepository
import org.springframework.stereotype.Component

/**
 * 도메인 포트(PartnerOutPort)와 JPA 리포지토리 사이를 중개하는 어댑터.
 */
@Component
class PartnerPersistenceAdapter(
    private val repo: PartnerJpaRepository,
) : PartnerOutPort {
    override fun findById(id: Long): Partner? =
        repo.findById(id).orElse(null)?.let { Partner(id = it.id!!, code = it.code, name = it.name, active = it.active) }
}
