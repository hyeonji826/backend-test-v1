package im.bigs.pg.application.payment.port.`in`

import im.bigs.pg.domain.payment.Payment

/**
 * 결제 생성/취소 유스케이스(입력 포트).
 */
interface PaymentUseCase {
    /** 승인(결제 생성) */
    fun pay(command: PaymentCommand): Payment

    /** 취소(전체/부분) */
    fun cancel(command: CancelCommand): Payment
}
