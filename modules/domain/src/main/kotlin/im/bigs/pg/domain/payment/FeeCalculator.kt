package im.bigs.pg.domain.payment

import im.bigs.pg.domain.partner.FeePolicy
import java.math.BigDecimal
import java.math.RoundingMode

object FeeCalculator {
    data class Result(
        // ex) 0.0300
        val appliedFeeRate: BigDecimal,
        // HALF_UP
        val feeAmount: BigDecimal,
        // amount - fee
        val netAmount: BigDecimal,
    )

    fun calculate(
        amount: BigDecimal,
        policy: FeePolicy,
    ): Result {
        // percentage는 정책에 ex) 0.0300 형태라고 가정
        val percentFee = amount.multiply(policy.percentage)
        // fixedFee가 null인 정책도 허용하므로 0으로 간주
        val rawFee = percentFee.add(policy.fixedFee ?: BigDecimal.ZERO)
        val fee = rawFee.setScale(0, RoundingMode.HALF_UP) // 금액 단위 원, HALF_UP
        val net = amount.subtract(fee)
        return Result(
            appliedFeeRate = policy.percentage.setScale(4, RoundingMode.HALF_UP),
            feeAmount = fee,
            netAmount = net,
        )
    }
}
