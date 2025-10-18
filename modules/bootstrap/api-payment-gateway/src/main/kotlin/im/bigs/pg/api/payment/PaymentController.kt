package im.bigs.pg.api.payment

import im.bigs.pg.api.config.InMemoryIdempotencyStore
import im.bigs.pg.api.payment.dto.CreatePaymentRequest
import im.bigs.pg.api.payment.dto.PaymentResponse
import im.bigs.pg.common.ConflictException
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.`in`.QueryPaymentsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses

/**
 * 결제 API 진입점.
 * - POST: 결제 생성
 * - POST: 결제 취소(전체/부분)
 */
@RestController
@RequestMapping("/api/v1/payments")
@Validated
class PaymentController(
    private val paymentUseCase: PaymentUseCase,
    private val queryPaymentsUseCase: QueryPaymentsUseCase,
    private val idemStore: InMemoryIdempotencyStore,
) {
    /**
     * 결제 생성.
     */
    @Operation(summary = "결제 승인 생성", description = "Idempotency-Key 헤더 필수. 성공 시 승인 결과 반환")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "승인 성공"),
            ApiResponse(responseCode = "400", description = "유효성 오류"),
            ApiResponse(responseCode = "409", description = "아이도템포턴시 충돌"),
            ApiResponse(responseCode = "422", description = "외부 PG 오류 매핑")
        ]
    )
    @PostMapping
    fun create(
        @Parameter(description = "Idempotency-Key", required = true)
        @RequestHeader(value = "Idempotency-Key", required = false) idemKey: String?,
        @RequestBody @jakarta.validation.Valid req: CreatePaymentRequest,
    ): ResponseEntity<PaymentResponse> {
        if (!idemKey.isNullOrBlank() && idemStore.isDuplicate(idemKey)) {
            throw ConflictException("Duplicate Idempotency-Key")
        }
        val saved =
            paymentUseCase.pay(
                PaymentCommand(
                    partnerId = req.partnerId,
                    amount = req.amount,
                    cardBin = req.cardBin?.takeIf { it.isNotBlank() }?.take(6),
                    cardLast4 = req.cardLast4,
                    productName = req.productName,
                ),
            )
        if (!idemKey.isNullOrBlank() && saved.id != null) {
            idemStore.put(idemKey, saved.id!!)
        }
        return ResponseEntity.ok(PaymentResponse.from(saved))
    }

    /** 취소(전체/부분). */
    @Operation(summary = "결제 취소", description = "전체/부분 취소. cancelAmount 미전송 시 전체 취소")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "취소 성공"),
            ApiResponse(responseCode = "404", description = "리소스 없음"),
            ApiResponse(responseCode = "422", description = "외부 PG 오류 매핑")
        ]
    )
    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
        @RequestBody req: im.bigs.pg.api.payment.dto.CancelPaymentRequest,
    ): ResponseEntity<PaymentResponse> {
        val p = paymentUseCase.cancel(im.bigs.pg.application.payment.port.`in`.CancelCommand(id, req.cancelAmount, req.reason))
        return ResponseEntity.ok(PaymentResponse.from(p))
    }
}
