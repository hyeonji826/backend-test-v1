package im.bigs.pg.external.pg

import im.bigs.pg.application.pg.port.out.*
import im.bigs.pg.domain.payment.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.util.Base64

/**
 * 데모용 Test PG 연동 클라이언트.
 * - 통일된 규약에 맞춰 API-KEY 헤더와 /api/v1/pay/credit-card, /api/v1/pay/cancel 엔드포인트를 호출합니다.
 * - HTTP 4xx/5xx 응답은 예외로 전파하여 상위 서비스가 적절히 매핑하도록 합니다.
 */
@Component
@Order(1)
class TestPgClient(
    private val rest: RestTemplate,
    // 환경변수 PG_BASE_URL/PG_API_KEY를 우선 사용하고, 없으면 기존 속성/기본값을 사용
    @Value("\${pg.test.base-url:\${PG_BASE_URL:http://localhost:18080}}") private val baseUrl: String,
    @Value("\${pg.test.api-key:\${PG_API_KEY:test-api-key}}") private val apiKey: String,
) : PgClientOutPort {
    private val log = LoggerFactory.getLogger(javaClass)

    // 데모: 모든 파트너를 지원한다고 가정
    override fun supports(partnerId: Long): Boolean = true

    override fun approve(request: PgApproveRequest): PgApproveResult {
        val raw = "${'$'}{request.partnerId}|${'$'}{request.amount}|${'$'}{request.cardLast4}|${'$'}{request.productName}"
        val enc = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        val payload = mapOf("enc" to enc)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            if (apiKey.isNotBlank()) set("API-KEY", apiKey)
        }
        val entity = HttpEntity(payload, headers)

        val url = "$baseUrl/api/v1/pay/credit-card"
        try {
            rest.postForEntity(url, entity, Map::class.java)
        } catch (e: org.springframework.web.client.HttpStatusCodeException) {
            when (e.statusCode.value()) {
                401 -> throw im.bigs.pg.common.UnauthorizedException("PG unauthorized: ${e.responseBodyAsString}")
                422 -> throw im.bigs.pg.common.UnprocessableException("PG approve failed: ${e.responseBodyAsString}")
                in 400..499 -> throw IllegalArgumentException("PG client error: ${e.statusCode.value()}")
                else -> throw e
            }
        }

        return PgApproveResult(
            approvalCode = "TESTPG-${System.currentTimeMillis()}",
            approvedAt = LocalDateTime.now(),
            status = PaymentStatus.APPROVED,
        )
    }

    override fun cancel(request: PgCancelRequest): PgCancelResult {
        val reasonPart = request.reason ?: ""
        val amountPart = request.cancelAmount?.toPlainString() ?: "0"
        val raw = "${'$'}{request.partnerId}|${'$'}{request.paymentId}|${amountPart}|${reasonPart}"
        val enc = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        val payload = mapOf("enc" to enc)
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            if (apiKey.isNotBlank()) set("API-KEY", apiKey)
        }
        val entity = HttpEntity(payload, headers)
        val url = "$baseUrl/api/v1/pay/cancel"
        // Let RestTemplate throw on non-2xx
        rest.postForEntity(url, entity, Map::class.java)
        return PgCancelResult(
            canceledAt = LocalDateTime.now(),
            status = PaymentStatus.CANCELED,
        )
    }
}
