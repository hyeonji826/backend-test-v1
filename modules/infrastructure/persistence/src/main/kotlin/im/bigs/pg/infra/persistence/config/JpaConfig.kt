package im.bigs.pg.infra.persistence.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * 인프라 계층 JPA 설정.
 * - 리포지토리/엔티티 패키지를 스캔하여 Spring Data JPA 구성을 활성화합니다.
 */
@Configuration
@EnableJpaRepositories(basePackages = ["im.bigs.pg.infra.persistence"])
@EntityScan(basePackages = ["im.bigs.pg.infra.persistence"])
class JpaConfig
