# API Payment Gateway (과제)

## 1. 개요

**프로젝트명**: API Payment Gateway (나노바나나 페이먼츠 사전과제)  
**핵심기능**:
- 결제 승인/취소 (외부 PG 연동: WireMock)
- 파트너별 수수료 정책 적용
- 커서 기반 페이지네이션 + 통계(summary)
- Idempotency-Key 기반 중복 방지

**한 줄 실행**
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\docker-e2e.ps1

Invoke-RestMethod http://localhost:18080/__admin/requests | ConvertTo-Json -Depth 6
```

2. 실행 환경
Docker / Docker Compose

Java 21 (Eclipse Temurin)

MariaDB, WireMock

Spring Boot 3.x (Kotlin)

3. 환경변수(고정 키 이름)
Key	예시값	설명
SPRING_PROFILES_ACTIVE	local-docker	도커 네트워크 프로파일
PG_BASE_URL	http://wiremock:8080	외부 PG 모킹 Base URL
PG_API_KEY	test-api-key	PG 호출 API-KEY (fail-api-key로 실패 응답 유도)

모든 코드/스크립트는 위 키 이름에 맞춰 동작합니다.

4. DB 스키마/시드
scripts/docker-e2e.ps1 실행 시 sql/scheme.sql 자동 적용

초기 시드

파트너: TEST, NEW

수수료 정책 예시: TEST=2.5%, NEW=3.0%

5. API 요약
5.1 결제 승인 생성
Method/Path: POST /api/v1/payments

Headers

Idempotency-Key: <string> (필수)

Request Body (예시)

json
코드 복사
{
  "partnerId": 1,
  "amount": 20000,
  "cardBin": "111122",
  "cardLast4": "3344",
  "productName": "demo"
}
Response (예시)

json
코드 복사
{
  "id": 123,
  "partnerId": 1,
  "amount": 20000,
  "appliedFeeRate": 0.0250,
  "feeAmount": 500,
  "netAmount": 19500,
  "cardLast4": "3344",
  "approvalCode": "TESTPG-...",
  "approvedAt": "2025-01-01T00:00:00Z",
  "status": "APPROVED",
  "createdAt": "2025-01-01T00:00:00Z"
}
5.2 결제 조회(커서 기반 + 통계)
Method/Path:
GET /api/v1/payments?partnerId={id}&status={APPROVED|CANCELED}&limit={n}&cursor={token}

Response (예시)

json
코드 복사
{
  "items": [ { "id": 123, "...": "..." } ],
  "summary": { "count": 10, "totalAmount": 200000, "totalNetAmount": 195000 },
  "nextCursor": "eyJjcmVh...",
  "hasNext": true
}
5.3 결제 취소(전체/부분)
Method/Path: POST /api/v1/payments/{id}/cancel

Request Body (전체/부분)

json
코드 복사
{ "reason": "user_request" }
json
코드 복사
{ "reason": "partial_refund", "cancelAmount": 5000 }
6. 외부 PG 모킹(WireMock)
Endpoint	용도	Header
POST /api/v1/pay/credit-card	승인	API-KEY
POST /api/v1/pay/cancel	취소	API-KEY

애플리케이션 컨테이너는 도커 네트워크 내 http://wiremock:8080 으로 호출합니다.

E2E 스크립트가 스텁 등록 및 저널 초기화를 수행합니다.

7. 빠른 시연 스크립트 (PowerShell)
powershell
코드 복사
# 1) 결제 승인 (Idempotency-Key 포함)
$body = @{ partnerId=1; amount=20000; cardBin="111122"; cardLast4="3344"; productName="demo" } | ConvertTo-Json
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/payments `
  -ContentType application/json `
  -Headers @{ "Idempotency-Key"="demo-001" } `
  -Body $body

# 2) 결제 조회 (limit=5)
Invoke-RestMethod "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=5"

# 3) 결제 취소 (전체)
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/payments/1/cancel `
  -ContentType application/json `
  -Body (@{ reason="user_request" } | ConvertTo-Json)

# 4) WireMock 저널 확인
Invoke-RestMethod http://localhost:18080/__admin/requests | ConvertTo-Json -Depth 6
8. 에러 재현 가이드
케이스	재현 방법	기대 응답
PG 실패 (422)	PG_API_KEY=fail-api-key 로 컨테이너 실행 후 취소 호출	422 JSON
인증 누락 (401)	PG_API_KEY 비우거나 bad-key 설정(해당 스텁 필요)	401 JSON
입력 검증 실패	음수 금액, 잘못된 카드 필드(빈값/자리수), 존재하지 않는 파트너	400/422
아이도템포턴시 (409)	동일 Idempotency-Key 로 승인 API 2회 호출	409 JSON

9. Swagger / OpenAPI
Swagger UI: http://localhost:8080/swagger-ui.html

OpenAPI JSON: http://localhost:8080/v3/api-docs

Gradle (Kotlin DSL)

kotlin
코드 복사
dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
}
application.yml (선택)

yaml
코드 복사
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
컨트롤러 예시 어노테이션

kotlin
코드 복사
@Operation(
  summary = "결제 승인 생성",
  description = "Idempotency-Key 헤더 필수. 성공 시 승인 결과 반환"
)
@ApiResponses(
  value = [
    ApiResponse(responseCode = "200", description = "승인 성공"),
    ApiResponse(responseCode = "400", description = "유효성 오류"),
    ApiResponse(responseCode = "409", description = "아이도템포턴시 충돌"),
    ApiResponse(responseCode = "422", description = "외부 PG 오류 매핑")
  ]
)
@PostMapping("/api/v1/payments")
fun createPayment(
  @Parameter(description = "Idempotency-Key", required = true)
  @RequestHeader("Idempotency-Key") key: String,
  @RequestBody req: PaymentCommand
): PaymentResponse { ... }
에러 스키마 모델(선택)

kotlin
코드 복사
@Schema(description = "표준 에러 응답")
data class ErrorResponse(
  @Schema(example = "422") val code: Int,
  @Schema(example = "INSUFFICIENT_LIMIT") val errorCode: String,
  @Schema(example = "Credit limit exceeded") val message: String,
  @Schema(example = "ref-xyz") val referenceId: String?
)
글로벌 예외 처리(요약)

kotlin
코드 복사
@RestControllerAdvice
class GlobalExceptionHandler {
  @ExceptionHandler(ValidationException::class)
  fun handleValidation(e: ValidationException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(422).body(ErrorResponse(422, "VALIDATION_ERROR", e.message ?: "Invalid", genRef()))

  @ExceptionHandler(IdempotencyConflict::class)
  fun handleIdem(e: IdempotencyConflict): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(409).body(ErrorResponse(409, "IDEMPOTENCY_CONFLICT", e.message ?: "Conflict", genRef()))

  // 401, 404, 422(PG) 등 추가
}
10. 에러 응답(JSON) 샘플
공통 포맷
json
코드 복사
{
  "code": 422,
  "errorCode": "INSUFFICIENT_LIMIT",
  "message": "Credit limit exceeded",
  "referenceId": "ref-xyz"
}
400/422 (입력 검증 실패)
json
코드 복사
{
  "code": 422,
  "errorCode": "VALIDATION_ERROR",
  "message": "amount must be >= 1, cardLast4 must be 4 digits",
  "referenceId": "req-20251018-001"
}
401 (인증 누락/잘못된 키)
json
코드 복사
{
  "code": 401,
  "errorCode": "UNAUTHORIZED",
  "message": "API-KEY missing or invalid",
  "referenceId": "ref-unauth"
}
404 (리소스 없음 / 파트너 없음)
json
코드 복사
{
  "code": 404,
  "errorCode": "NOT_FOUND",
  "message": "Partner not found: id=999999",
  "referenceId": "req-20251018-002"
}
409 (아이도템포턴시 충돌)
json
코드 복사
{
  "code": 409,
  "errorCode": "IDEMPOTENCY_CONFLICT",
  "message": "Duplicate request with the same Idempotency-Key",
  "referenceId": "req-20251018-003"
}
422 (PG 한도 초과 등 외부 에러 매핑)
json
코드 복사
{
  "code": 422,
  "errorCode": "OVER_CANCEL",
  "message": "Cancel amount exceeds approved",
  "referenceId": "pg-ref-cx"
}
11. 배경/구조 요약
초기 하드코드 수수료(3%+100원) → 정책 테이블 기반 계산으로 전환

멀티모듈, 헥사고널(Ports & Adapters) 아키텍처 유지

modules/domain: FeePolicy, Payment 등 (프레임워크 의존 금지)

modules/application: UseCase/Port, PaymentService

modules/infrastructure/persistence: JPA 엔티티/리포지토리, 페이징/요약 어댑터

modules/external/pg-client: PG 어댑터 (Mock/TestPay)

modules/bootstrap/api-payment-gateway: Spring Boot API

12. 필수 요구 사항 충족 기준
결제 생성: 금액/적용 수수료율/수수료/정산금/카드 식별(마스킹)/승인번호/승인시각/상태 저장

조회 API: summary가 items와 동일 집합 기준으로 정확히 집계

커서 페이지네이션: createdAt DESC, id DESC 정렬 기준 일관성, nextCursor/hasNext 정확

수수료 정책: effective_from 기준 최신 정책 적용, HALF_UP 반올림

민감정보 저장/로깅 금지

의미 있는 단위/통합 테스트 포함

13. 빌드/실행
bash
코드 복사
./gradlew build
./gradlew test
./gradlew :modules:bootstrap:api-payment-gateway:bootRun
./gradlew ktlintCheck | ktlintFormat
기본 포트: 8080

14. 제출물
GitHub 저장소 링크(메일 본문에 채용공고명/실명 포함)

포함: 구현 코드, 테스트, README(본 문서), 변경이력, 선택 구현 설명(선택)

15. 평가 기준
아키텍처 일관성(모듈 경계, 포트-어댑터, 의존 역전)

도메인 모델링/가독성(KDoc, 네이밍)

기능 정확성(통계 일치, 커서 페이징 동작, 수수료 계산)

테스트 품질(결정적/빠름/커버리지)

보안/개인정보 처리(민감정보 최소 저장, 로깅 배제)

변경 이력 품질(작은 단위, 의미 있는 커밋)

16. 참고자료
과제 내 연동 대상 API 문서: https://api-test-pg.bigs.im/docs/index.html

17. 주의사항
이 디렉터리(backend-test-v1)만 압축/전달됩니다. 외부 경로 참조 금지

본 과제 관련 질의응답 없음

제출물을 기준으로 면접 시 코드리뷰 진행

