package im.bigs.pg.domain.payment

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 결제 이력의 스냅샷.
 * - 저장 시점의 수수료율/수수료/정산금 등 계산 결과를 그대로 보존합니다.
 * - 카드 정보는 최소한의 식별 정보만 저장(마스킹/부분 저장)하도록 설계되었습니다.
 *
 * @property partnerId 제휴사 식별자
 * @property amount 결제 금액(정수 금액 권장)
 * @property appliedFeeRate 적용된 수수료율(저장 시점의 값)
 * @property feeAmount 수수료 금액
 * @property netAmount 공제 후 금액(정산금)
 * @property cardBin 선택 저장되는 카드 BIN(없을 수 있음)
 * @property cardLast4 마스킹용 마지막 4자리(없을 수 있음)
 * @property approvalCode 승인 식별 코드
 * @property approvedAt 승인 시각(UTC)
 * @property status 상태(승인/취소 등)
 * @property createdAt 생성 시각(정렬/커서 키)
 * @property updatedAt 갱신 시각
 */
data class Payment(
    val id: Long? = null,
    val partnerId: Long,
    val amount: BigDecimal,
    val appliedFeeRate: BigDecimal,
    val feeAmount: BigDecimal,
    val netAmount: BigDecimal,
    val cardBin: String? = null,
    val cardLast4: String? = null,
    val approvalCode: String,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val approvedAt: LocalDateTime,
    val status: PaymentStatus,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

/** 결제 상태. 취소 시에도 원본 행을 유지하고 상태만 변경하는 방식 등을 고려합니다. */

/** 결제 상태.
 * - 승인(Approved), 취소(Canceled) 등 단순 상태를 표현합니다.
 */
enum class PaymentStatus { APPROVED, CANCELED }

/** 조회 API의 통계 응답에 사용되는 값 모음. */

/** 조회 API의 통계 응답에 사용되는 값 모음. */
data class PaymentSummary(
    val count: Long,
    val totalAmount: BigDecimal,
    val totalNetAmount: BigDecimal,
)
