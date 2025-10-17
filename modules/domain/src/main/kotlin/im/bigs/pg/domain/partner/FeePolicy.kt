package im.bigs.pg.domain.partner

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 제휴사별 수수료 정책.
 * - effectiveFrom 시점부터 적용되는 정책으로, 동일 제휴사에 여러 버전이 존재할 수 있습니다.
 * - percentage(비율)와 fixedFee(고정 수수료)를 함께/단독으로 사용할 수 있습니다.
 * - 시간대는 UTC 기준을 권장합니다.
 *
 * @property partnerId 정책이 적용될 제휴사 식별자
 * @property effectiveFrom 정책 유효 시작 시점(UTC)
 * @property percentage 비율 수수료(예: 0.0235 = 2.35%)
 * @property fixedFee 고정 수수료(없으면 null)
 */
data class FeePolicy(
    val id: Long? = null,
    val partnerId: Long,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val effectiveFrom: LocalDateTime,
    val percentage: BigDecimal, // e.g., 0.0235 (2.35%)
    val fixedFee: BigDecimal? = null,
)
