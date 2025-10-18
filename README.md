# API Payment Gateway (과제)

1. 개요

프로젝트명: API Payment Gateway (과제)

핵심기능: 결제 승인/취소, 파트너별 수수료 정책, 커서 기반 페이지네이션, 외부 PG 모킹(WireMock), 아이도템포턴시

한 줄 실행:

powershell -ExecutionPolicy Bypass -File .\scripts\docker-e2e.ps1
Invoke-RestMethod http://localhost:18080/__admin/requests | ConvertTo-Json -Depth 6

2. 실행 환경

Docker / Docker Compose

Java 21 (eclipse-temurin)

MariaDB, WireMock

3. 환경변수(고정 키 이름)
Key	예시값	설명
SPRING_PROFILES_ACTIVE	local-docker	도커 네트워크 프로파일
PG_BASE_URL	http://wiremock:8080	외부 PG 모킹 베이스 URL
PG_API_KEY	test-api-key	PG 호출 API-KEY (실패 유도 시 fail-api-key 또는 빈값)
4. DB 스키마/시드

scripts/docker-e2e.ps1 실행 시 scheme.sql 자동 적용

시드: 파트너(TEST, NEW), 파트너별 수수료 정책(예: TEST=2.5%, NEW=3.0%)

5. API 요약

결제 승인 생성: POST /api/v1/payments

헤더: Idempotency-Key: <string>

결제 조회(커서 기반): GET /api/v1/payments?partnerId={id}&status={APPROVED|CANCELED}&limit={n}&cursor={token}

결제 취소(전체/부분): POST /api/v1/payments/{id}/cancel

바디: { "reason": "...", "cancelAmount": 5000 } (전체 취소 시 cancelAmount 생략)

6. 외부 PG 모킹(WireMock)

승인: POST /api/v1/pay/credit-card (헤더 API-KEY)

취소: POST /api/v1/pay/cancel (헤더 API-KEY)

앱 컨테이너는 도커 네트워크 서비스명으로 호출: http://wiremock:8080

7. 빠른 시연 스크립트
# 승인 (Idempotency-Key 포함)
$body = @{ partnerId=1; amount=20000; cardBin="111122"; cardLast4="3344"; productName="demo" } | ConvertTo-Json
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/payments `
 -ContentType application/json -Headers @{ "Idempotency-Key"="demo-001" } -Body $body

# 조회 (limit=5)
Invoke-RestMethod "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=5"

# 취소(전체)
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/payments/1/cancel `
 -ContentType application/json -Body (@{ reason="user_request" } | ConvertTo-Json)

# WireMock 저널 확인
Invoke-RestMethod http://localhost:18080/__admin/requests | ConvertTo-Json -Depth 6

8. 에러 재현 가이드

PG 실패(422): PG_API_KEY=fail-api-key로 컨테이너 기동 후 취소 요청

인증 누락(401): PG_API_KEY를 비우거나 bad-key로 실행(스텁 필요)

입력 검증(400/422): 음수 금액, 카드 필드 빈값/자리수 오류, 존재하지 않는 파트너

아이도템포턴시(409): 동일 Idempotency-Key로 중복 승인 요청

9. 테스트

도메인: 수수료 라운딩/상태 전이

인프라 통합: Testcontainers(MariaDB) + WireMock(200/422)

10. Swagger / OpenAPI 문서

UI: http://localhost:8080/swagger-ui.html

스펙: http://localhost:8080/v3/api-docs

에러 응답(JSON) 샘플 (제출 본문에 그대로 첨부 권장)
공통 포맷
{
  "code": 422,
  "errorCode": "INSUFFICIENT_LIMIT",
  "message": "Credit limit exceeded",
  "referenceId": "ref-xyz"
}

400/422 (입력 검증 실패)
{
  "code": 422,
  "errorCode": "VALIDATION_ERROR",
  "message": "amount must be >= 1, cardLast4 must be 4 digits",
  "referenceId": "req-20251018-001"
}

401 (인증 누락/잘못된 키)
{
  "code": 401,
  "errorCode": "UNAUTHORIZED",
  "message": "API-KEY missing or invalid",
  "referenceId": "ref-unauth"
}

404 (리소스 없음 / 파트너 없음)
{
  "code": 404,
  "errorCode": "NOT_FOUND",
  "message": "Partner not found: id=999999",
  "referenceId": "req-20251018-002"
}

409 (아이도템포턴시 충돌)
{
  "code": 409,
  "errorCode": "IDEMPOTENCY_CONFLICT",
  "message": "Duplicate request with the same Idempotency-Key",
  "referenceId": "req-20251018-003"
}

422 (PG 한도 초과 등 외부 에러 매핑)
{
  "code": 422,
  "errorCode": "OVER_CANCEL",
  "message": "Cancel amount exceeds approved",
  "referenceId": "pg-ref-cx"
}

Swagger(OpenAPI) 연동 가이드
Gradle (Kotlin DSL)
dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
}

애플리케이션 설정 (선택)

application.yml

springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html

컨트롤러 예시 어노테이션
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
@Schema(description = "표준 에러 응답")
data class ErrorResponse(
  @Schema(example = "422") val code: Int,
  @Schema(example = "INSUFFICIENT_LIMIT") val errorCode: String,
  @Schema(example = "Credit limit exceeded") val message: String,
  @Schema(example = "ref-xyz") val referenceId: String?
)

글로벌 예외 처리(요약)
@RestControllerAdvice
class GlobalExceptionHandler {
  @ExceptionHandler(ValidationException::class)
  fun handleValidation(e: ValidationException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(422).body(ErrorResponse(422, "VALIDATION_ERROR", e.message ?: "Invalid", genRef()))

  @ExceptionHandler(IdempotencyConflict::class)
  fun handleIdem(e: IdempotencyConflict): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(409).body(ErrorResponse(409, "IDEMPOTENCY_CONFLICT", e.message ?: "Conflict", genRef()))

  // ... 401, 404, 422(PG) 등 추가
}

제출 체크리스트(최종)

 한 줄 실행 + WireMock 저널 확인 명령

 ENV 표 (키 이름 명확)

 성공/실패 응답 샘플(JSON) 포함

 Swagger 접속 경로 기재 (/swagger-ui.html, /v3/api-docs)

 에러 재현 방법(422/401/400/409) 안내

 커서 페이징/수수료 정책 설명 한 줄

본 과제는 나노바나나 페이먼츠의 “결제 도메인 서버”를 주제로, 백엔드 개발자의 설계·구현·테스트 역량을 평가하기 위한 사전 과제입니다. 제공된 멀티모듈 + 헥사고널 아키텍처 기반 코드를 바탕으로 요구사항을 충족하는 기능을 완성해 주세요.

주의: 이 디렉터리(`backend-test-v1`)만 압축/전달됩니다. 외부 경로를 참조하지 않도록 README/코드/스크립트를 유지해 주세요.

## 1. 배경 시나리오
- 본 서비스는 결제대행사 “나노바나나 페이먼츠”의 결제 도메인 서버입니다.
- 현재는 제휴사가 없어 “목업 PG”만 연동되어 있으며, 결제는 항상 성공합니다.
- 정산금 계산식은 임시로 “하드코드(3% + 100원)” 되어 있습니다.

여러 제휴사와 연동을 시작하면서 다음이 필요합니다.
1) 새로운 결제 제휴사 연동(기본 스켈레톤 제공)
2) 결제 내역 조회 API 제공(통계 포함, 커서 기반 페이지네이션)
3) 제휴사별 수수료 정책 적용(하드코드 제거, 정책 테이블 기반)

## 2. 과제 목표
아래 항목을 모두 구현/보강하고, 테스트로 증명해 주세요.

1) 결제 생성
- 엔드포인트: POST `/api/v1/payments`
- 내용: 결제 승인(외부 PG 연동) 후, 수수료/정산금 계산 결과를 포함하여 저장
- 주의: 현재 `PaymentService`는 하드코드된 수수료(3% + 100원)를 사용합니다. 제휴사별 정책(percentage, fixedFee, effective_from)에 따라 계산하도록 리팩터링하세요.  
  또한 반드시 [11. 참고자료](#11-참고자료) 의 과제 내 연동 대상 API 문서를 참고하여 TestPg 와 Rest API 를 통한 연동을 진행해야 합니다. 

2) 결제 내역 조회 + 통계
- 엔드포인트: GET `/api/v1/payments`
- 쿼리: `partnerId`, `status`, `from`, `to`, `cursor`, `limit`
- 응답: `items[]`, `summary{count,totalAmount,totalNetAmount}`, `nextCursor`, `hasNext`
- 요구: 통계는 반드시 필터와 동일한 집합을 대상으로 계산되어야 하며, 커서 기반 페이지네이션을 사용해야 합니다.

3) 제휴사별 수수료 정책
- 스키마: `sql/scheme.sql` 의 `partner`, `partner_fee_policy`, `payment` 참조(필요시 보완/수정 가능)
- 규칙: `effective_from` 기준 가장 최근(<= now) 정책을 적용, 금액은 HALF_UP로 반올림
- 보안: 카드번호 등 민감정보는 저장/로깅 금지(제공 코드도 마스킹/부분 저장만 수행)

## 3. 제공 코드 개요(헥사고널)
- `modules/domain`: 순수 도메인 모델/유틸(FeePolicy, Payment, FeeCalculator 등)
- `modules/application`: 유스케이스/포트(PaymentUseCase, QueryPaymentsUseCase, Repository/PgClient 포트, PaymentService 등)
  - 의도적으로 PaymentService에 “하드코드 수수료 계산”이 남아 있습니다. 이를 정책 기반으로 개선하세요.
- `modules/infrastructure/persistence`: JPA 엔티티·리포지토리·어댑터(pageBy/summary 제공)
- `modules/external/pg-client`: PG 연동 어댑터(Mock, TestPay 예시)
- `modules/bootstrap/api-payment-gateway`: 실행 가능한 Spring Boot API(Controller, 시드 데이터)

아키텍처 제약
- 멀티모듈 경계/의존 역전/포트-어댑터 패턴을 유지할 것
- `domain`은 프레임워크 의존 금지(순수 Kotlin)

## 4. 필수 요구 사항
- 결제 생성 시 저장 레코드에 다음 필드가 정확히 기록됨: 금액, 적용 수수료율, 수수료, 정산금, 카드 식별(마스킹), 승인번호, 승인시각, 상태
- 조회 API에서 필터 조합별 `summary`가 `items`와 동일 집합을 정확히 집계
- 커서 페이지네이션이 정렬 키(`createdAt desc, id desc`) 기반으로 올바르게 동작(다음 페이지 유무/커서 일관성)
- 제휴사별 수수료 정책(비율/고정/시점)이 적용되어 계산 결과가 맞음
- 모든 신규/수정 로직에 대해 의미 있는 단위/통합 테스트 존재, 빠르고 결정적

## 5. 개발 환경 & 실행 방법
- JDK 21, Gradle Wrapper 사용
- H2 인메모리 DB 기본 실행(필요 시 schema/data/migration 구성 변경 가능)

명령어
```bash
./gradlew build                  # 컴파일 + 모든 테스트
./gradlew test                   # 테스트만
./gradlew :modules:bootstrap:api-payment-gateway:bootRun   # API 실행
./gradlew ktlintCheck | ktlintFormat  # 코드 스타일 검사/자동정렬
```
기본 포트: 8080

## 6. API 사양(요약)
1) 결제 생성
```
POST /api/v1/payments
{
  "partnerId": 1,
  "amount": 10000,
  "cardBin": "123456",
  "cardLast4": "4242",
  "productName": "샘플"
}

200 OK
{
  "id": 99,
  "partnerId": 1,
  "amount": 10000,
  "appliedFeeRate": 0.0300,
  "feeAmount": 400,
  "netAmount": 9600,
  "cardLast4": "4242",
  "approvalCode": "...",
  "approvedAt": "2025-01-01T00:00:00Z",
  "status": "APPROVED",
  "createdAt": "2025-01-01T00:00:00Z"
}
```

2) 결제 조회(통계+커서)
```
GET /api/v1/payments?partnerId=1&status=APPROVED&from=2025-01-01T00:00:00Z&to=2025-01-02T00:00:00Z&limit=20&cursor=

200 OK
{
  "items": [ { ... }, ... ],
  "summary": { "count": 35, "totalAmount": 35000, "totalNetAmount": 33950 },
  "nextCursor": "ey1...",
  "hasNext": true
}
```

## 7. 데이터베이스 가이드
- 기준 테이블(예시):
  - `partner(id, code, name, active)`
  - `partner_fee_policy(id, partner_id, effective_from, percentage, fixed_fee)`
  - `payment(id, partner_id, amount, applied_fee_rate, fee_amount, net_amount, card_bin, card_last4, approval_code, approved_at, status, created_at, updated_at)`
- 인덱스 권장: `payment(created_at desc, id desc)`, `payment(partner_id, created_at desc)`, 검색 조건 컬럼
- 정확한 스키마/인덱스는 요구사항을 만족하는 선에서 자유롭게 보완 가능

## 8. 제출물
- github 저장소 링크를 사전과제 전달 메일로 회신. (메일 본문에 채용공고 명 / 실명 기재 필수)
- 포함 사항: 구현 코드, 테스트, 간단 사용가이드(필요 시 README 보강), 변경이력, 추가 선택 구현 설명(선택)

## 9. 평가 기준
- 아키텍처 일관성(모듈 경계, 포트-어댑터, 의존 역전)
- 도메인 모델링 적절성 및 가독성(KDoc, 네이밍)
- 기능 정확성(통계 일치, 커서 페이징 동작, 수수료 계산)
- 테스트 품질(결정적/빠름/커버리지)
- 보안/개인정보 처리(민감정보 최소 저장, 로깅 배제)
- 변경 이력 품질(의미 있는 커밋 메시지, 작은 단위 변경)

## 10. 선택 과제(가산점)
- 추가 제휴사 연동(Adapter 추가 및 전략 선택)
- 오픈API 문서화(springdoc 등) 또는 간단한 운영지표(로그/메트릭)
- MariaDB 등 외부 DB로 전환(docker-compose 포함) 및 마이그레이션 도구 적용

## 11. 참고자료
- [과제 내 연동 대상 API 문서](https://api-test-pg.bigs.im/docs/index.html)

## 12. 주의사항
- 전달한 본 프로젝트는 정상동작하지 않습니다. 요구사항을 포함해, 정상 동작을 목표로 진행하세요.
- 본 과제와 관련한 어떠한 질문도 받지 않습니다.
- 제출물을 기준으로 면접시 코드리뷰를 진행합니다. 이를 고려해주세요. 

행운을 빕니다. 읽기 쉬운 코드, 일관된 설계, 신뢰할 수 있는 테스트를 기대합니다.


## Docker tips: WireMock name conflict and Compose warning

- Compose version key obsolete: This repository uses Docker Compose v2 syntax. If you see a warning like:
  - the attribute `version` is obsolete, it will be ignored
  It is safe to ignore, but we removed the `version` key from docker-compose.yml to silence it.

- WireMock container name conflict: If a standalone container named `wiremock` already exists, Compose will fail to start the service with a name conflict. Recover with:
  - PowerShell:
    - `docker stop wiremock 2>$null; docker rm wiremock 2>$null`
    - `docker compose up -d wiremock`

- Start both services (MariaDB + WireMock):
  - `docker compose up -d mariadb wiremock`

- Check status:
  - `docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"`




### PowerShell pitfalls: avoid $PID collision when calling APIs

PowerShell variable names are case-insensitive and `$PID` is a reserved, read-only variable that holds the current shell process id. Using `$pid` will refer to the same reserved variable and will not contain your database partner id. This can cause `POST /api/v1/payments` to fail with 500 (partner not found) before the PG call is attempted, and WireMock will show no requests.

Use a different variable name (e.g., `$partnerId`) and ConvertTo-Json for the request body:

```powershell
# Resolve the TEST partner id from MariaDB
$partnerId = docker exec -i mariadb mariadb -uappuser -papp-pass appdb -N -e "SELECT id FROM partner WHERE code='TEST' LIMIT 1;"

# Create payment (POST)
$body = @{ 
  partnerId  = [int]$partnerId
  amount     = 10000
  cardBin    = "123456"
  cardLast4  = "4242"
  productName= "sample"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/payments" -ContentType "application/json" -Body $body

# Query payments (GET)
Invoke-RestMethod -Method Get -Uri ("http://localhost:8080/api/v1/payments?partnerId={0}&status=APPROVED&limit=5" -f $partnerId)

# Check PG (WireMock) request journal
Invoke-RestMethod -Method Get -Uri "http://localhost:18080/__admin/requests" | ConvertTo-Json -Depth 6
```

If the POST still returns 500, collect the response body and the last ~50 lines of the API logs from `bootRun` and share them for diagnosis.
