package im.bigs.pg.domain.calculation

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 금액 관련 계산 유틸리티.
 * - 금액 단위 및 반올림 규칙은 테스트/요구사항에서 정의된 정책에 따릅니다.
 * - 정수 금액(예: KRW) 사용을 전제로 하며, 반올림은 HALF_UP 규칙을 적용합니다.
 * - 비즈니스 흐름(승인/저장 등)과 분리된 순수 계산 역할만 수행합니다.
 *
 * See also: 수수료 정책(FeePolicy) 모델
 */
object FeeCalculator {
    /**
     * 주어진 금액과 수수료율(및 선택적 고정 수수료)로 수수료와 정산금을 계산합니다.
     *
     * @param amount 결제 금액(소수점 없는 정수 금액 권장)
     * @param rate 수수료율(예: 0.0235 = 2.35%)
     * @param fixed 고정 수수료(없으면 null)
     * @return Pair(feeAmount, netAmount) — (수수료, 공제후 금액)
     * @throws IllegalArgumentException amount 또는 rate 가 음수인 경우
     */
    fun calculateFee(
        amount: BigDecimal,
        rate: BigDecimal,
        fixed: BigDecimal? = null,
    ): Pair<BigDecimal, BigDecimal> {
        require(amount >= BigDecimal.ZERO) { "amount must be >= 0" }
        require(rate >= BigDecimal.ZERO) { "rate must be >= 0" }
        val percentageFee = amount.multiply(rate).setScale(0, RoundingMode.HALF_UP)
        val fee = if (fixed != null) percentageFee + fixed else percentageFee
        val net = amount - fee
        return fee to net
    }
}
