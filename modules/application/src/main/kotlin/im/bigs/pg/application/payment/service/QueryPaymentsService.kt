package im.bigs.pg.application.payment.service

import im.bigs.pg.application.payment.port.`in`.*
import im.bigs.pg.domain.payment.PaymentSummary
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64

/**
 * 결제 이력 조회 유스케이스 구현체.
 * - 커서 토큰은 createdAt/id를 안전하게 인코딩해 전달/복원합니다.
 * - 통계는 조회 조건과 동일한 집합을 대상으로 계산됩니다.
 */
@Service
class QueryPaymentsService(
    private val paymentRepository: im.bigs.pg.application.payment.port.out.PaymentOutPort,
) : QueryPaymentsUseCase {
    /**
     * 필터를 기반으로 결제 내역을 조회합니다.
     *
     * 커서 기반 페이지네이션과 동일 조건의 통계를 함께 제공합니다.
     *
     * @param filter 파트너/상태/기간/커서/페이지 크기
     * @return 조회 결과(목록/통계/커서)
     */
    override fun query(filter: QueryFilter): QueryResult {
        val (cursorCreatedAt, cursorId) = decodeCursor(filter.cursor)

        val query = im.bigs.pg.application.payment.port.out.PaymentQuery(
            partnerId = filter.partnerId,
            status = filter.status?.let { im.bigs.pg.domain.payment.PaymentStatus.valueOf(it) },
            from = filter.from,
            to = filter.to,
            limit = filter.limit,
            cursorCreatedAt = cursorCreatedAt?.let { java.time.LocalDateTime.ofInstant(it, java.time.ZoneOffset.UTC) },
            cursorId = cursorId,
        )
        val page = paymentRepository.findBy(query)

        val summaryFilter = im.bigs.pg.application.payment.port.out.PaymentSummaryFilter(
            partnerId = filter.partnerId,
            status = filter.status?.let { im.bigs.pg.domain.payment.PaymentStatus.valueOf(it) },
            from = filter.from,
            to = filter.to,
        )
        val proj = paymentRepository.summary(summaryFilter)
        val summary = PaymentSummary(
            count = proj.count,
            totalAmount = proj.totalAmount,
            totalNetAmount = proj.totalNetAmount,
        )

        val nextCursor = if (page.hasNext) {
            val nCreatedAt = page.nextCursorCreatedAt?.toInstant(java.time.ZoneOffset.UTC)
            encodeCursor(nCreatedAt, page.nextCursorId)
        } else null

        return QueryResult(
            items = page.items,
            summary = summary,
            nextCursor = nextCursor,
            hasNext = page.hasNext,
        )
    }

    /** 다음 페이지 이동을 위한 커서 인코딩. */
    private fun encodeCursor(createdAt: Instant?, id: Long?): String? {
        if (createdAt == null || id == null) return null
        val raw = "${createdAt.toEpochMilli()}:$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    /** 요청으로 전달된 커서 복원. 유효하지 않으면 null 커서로 간주합니다. */
    private fun decodeCursor(cursor: String?): Pair<Instant?, Long?> {
        if (cursor.isNullOrBlank()) return null to null
        return try {
            val raw = String(Base64.getUrlDecoder().decode(cursor))
            val parts = raw.split(":")
            val ts = parts[0].toLong()
            val id = parts[1].toLong()
            Instant.ofEpochMilli(ts) to id
        } catch (e: Exception) {
            null to null
        }
    }
}
