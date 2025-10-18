package im.bigs.pg.external.pg

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class WebClientConfig(
    @Value("\${pg.test.connect-timeout-ms:2000}") private val connectTimeoutMs: Int,
    @Value("\${pg.test.read-timeout-ms:3000}") private val readTimeoutMs: Int,
) {
    @Bean
    fun restTemplate(): RestTemplate {
        val factory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeoutMs)
                setReadTimeout(readTimeoutMs)
            }
        return RestTemplate(factory)
    }
}
