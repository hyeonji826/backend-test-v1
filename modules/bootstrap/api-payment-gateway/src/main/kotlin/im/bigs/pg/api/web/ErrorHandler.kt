package im.bigs.pg.api.web

import im.bigs.pg.common.ConflictException
import im.bigs.pg.common.NotFoundException
import im.bigs.pg.common.UnprocessableException
import im.bigs.pg.common.UnauthorizedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@org.springframework.web.bind.annotation.RestControllerAdvice
class ErrorHandler {
    data class ErrorBody(
        val code: Int,
        val errorCode: String,
        val message: String,
        val referenceId: String? = null,
        val details: Map<String, String?>? = null,
    )

    private fun genRef(): String = "req-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(java.time.ZoneOffset.UTC)
        .format(java.time.Instant.now())

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorBody(404, "NOT_FOUND", e.message ?: "Not found", genRef())
        )

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(e: ConflictException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorBody(409, "IDEMPOTENCY_CONFLICT", e.message ?: "Conflict", genRef())
        )

    @ExceptionHandler(UnprocessableException::class)
    fun handleUnprocessable(e: UnprocessableException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorBody(422, "UNPROCESSABLE", e.message ?: "Unprocessable", genRef())
        )

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorBody(401, "UNAUTHORIZED", e.message ?: "Unauthorized", genRef())
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorBody> {
        val details = e.bindingResult.allErrors.associate { err ->
            val field = (err as? FieldError)?.field ?: err.objectName
            field to err.defaultMessage
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorBody(422, "VALIDATION_ERROR", "Validation failed", genRef(), details)
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(e: IllegalArgumentException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorBody(400, "BAD_REQUEST", e.message ?: "Bad request", genRef())
        )
}
