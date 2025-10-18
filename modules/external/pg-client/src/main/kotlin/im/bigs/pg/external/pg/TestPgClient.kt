package im.bigs.pg.external.pg

import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.PaymentStatus
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

/**
 * 데모용 Test PG 연동 클라이언트.
 * - 과제 내 연동 대상 API 문서에 맞춰 base-url만 조정하면 됩니다.
 * - 승인 응답은 데모 특성상 성공 케이스로 고정합니다.
 */
@Component
class TestPgClient(
    private val rest: RestTemplate,
    @Value("\${pg.test.base-url}") private val baseUrl: String,
    @Value("\${pg.test.api-key}") private val apiKey: String,
) : PgClientOutPort {

    // 데모: 모든 파트너를 지원한다고 가정
    override fun supports(partnerId: Long): Boolean = true

    override fun approve(request: PgApproveRequest): PgApproveResult {
        // 민감정보 최소 전송: cardLast4만 전달 (cardBin은 null 허용)
        val payload = mapOf(
            "partnerId" to request.partnerId,
            "amount" to request.amount.toPlainString(),
            "cardLast4" to request.cardLast4,
            "productName" to request.productName,
        )
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Bearer $apiKey")
        }
        val entity = HttpEntity(payload, headers)

        // 실제 문서의 승인 엔드포인트 명세에 맞춰 경로만 바꾸면 됨
        val url = "$baseUrl/api/approve"
        // 데모환경: 항상 승인이라 응답을 사용하지 않고 성공 케이스만 구성
        rest.postForEntity(url, entity, Map::class.java)

        return PgApproveResult(
            approvalCode = "TESTPG-${System.currentTimeMillis()}",
            approvedAt = LocalDateTime.now(),
            status = PaymentStatus.APPROVED,
        )
    }
}
