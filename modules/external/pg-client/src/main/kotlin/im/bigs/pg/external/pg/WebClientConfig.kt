package im.bigs.pg.external.pg

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class PgHttpConfig {
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
