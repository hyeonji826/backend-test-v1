package im.bigs.pg.infra.persistence.partner.repository

import im.bigs.pg.infra.persistence.partner.entity.PartnerEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 파트너 마스터 조회용 JPA 리포지토리. */
interface PartnerJpaRepository : JpaRepository<PartnerEntity, Long>
