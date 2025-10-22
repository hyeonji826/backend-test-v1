# API Payment Gateway (사전 과제)

## 1. 프로젝트 구성 (요약)

### 핵심 기능
- 결제 승인·취소 (WireMock)
- Idempotency-Key 중복 방지
- 파트너별 수수료 정책
- 커서 페이지네이션 + 통계
- 헥사고널 아키텍처 (Ports & Adapters)

### 가산점/개선
- 정책 테이블 기반 수수료
- 다중 PG (전략 패턴)
- SpringDoc OpenAPI
- MariaDB + Docker Compose
- Spring Boot Actuator
- 보안 로깅
- PowerShell E2E 스크립트

### 모듈 구조
```
modules/
├─ domain/                     # 순수 도메인 (의존성 없음)
├─ application/                # 유스케이스, Port 인터페이스
├─ infrastructure/persistence/ # JPA 엔티티, 리포지토리, 어댑터
├─ external/pg-client/         # PG 연동 (Mock/TestPg)
└─ bootstrap/api-payment-gateway/ # Spring Boot 진입점
```

---

## 2. 빠른 실행

### 전체 구동
```bash
docker-compose up -d
powershell -ExecutionPolicy Bypass -File .\scripts\docker-e2e.ps1
```

### 상태 확인
```bash
curl http://localhost:8080/actuator/health
```

<!-- 링크 제거: 면접 환경에서 열리지 않을 수 있어 제외 -->

---

## 3. API 엔드포인트 (핵심 코드)

### 결제 승인 생성 (@PostMapping)
```34:66:modules/bootstrap/api-payment-gateway/src/main/kotlin/im/bigs/pg/api/payment/PaymentController.kt
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
```

### 결제 목록 조회 (커서+통계, @GetMapping)
```46:106:modules/bootstrap/api-payment-gateway/src/main/kotlin/im/bigs/pg/bootstrap/api/web/PaymentQueryController.kt
@Validated
@RestController
@RequestMapping("/api/v1/payments")
class PaymentQueryController(
    private val svc: QueryPaymentsAdapterService,
) {
    @GetMapping
    fun getPayments(
        @RequestParam(required = false) partnerId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): ResponseEntity<PaymentsResponse> {
        val r =
            svc.execute(
                Query(
                    partnerId = partnerId,
                    status = status,
                    from = from,
                    to = to,
                    limit = limit,
                    cursor = cursor,
                ),
            )
        val iso = java.time.format.DateTimeFormatter.ISO_INSTANT
        val itemsDto = r.items.map { p ->
            PaymentItemDto(
                id = p.id,
                partnerId = p.partnerId,
                amount = p.amount,
                appliedFeeRate = p.appliedFeeRate,
                feeAmount = p.feeAmount,
                netAmount = p.netAmount,
                cardBin = p.cardBin,
                cardLast4 = p.cardLast4,
                approvalCode = p.approvalCode,
                approvedAt = iso.format(p.approvedAt.toInstant(java.time.ZoneOffset.UTC)),
                status = p.status.name,
                createdAt = iso.format(p.createdAt.toInstant(java.time.ZoneOffset.UTC)),
                updatedAt = iso.format(p.updatedAt.toInstant(java.time.ZoneOffset.UTC)),
            )
        }
        val body =
            PaymentsResponse(
                items = itemsDto,
                summary =
                    SummaryDto(
                        count = r.summary.count,
                        totalAmount = r.summary.totalAmount.toPlainString(),
                        totalNetAmount = r.summary.totalNetAmount.toPlainString(),
                    ),
                nextCursor = r.nextCursor,
                hasNext = r.hasNext,
            )
        return ResponseEntity.ok(body)
    }
}
```

### 결제 취소 (전체/부분, @PostMapping)
```68:84:modules/bootstrap/api-payment-gateway/src/main/kotlin/im/bigs/pg/api/payment/PaymentController.kt
    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
        @RequestBody req: im.bigs.pg.api.payment.dto.CancelPaymentRequest,
    ): ResponseEntity<PaymentResponse> {
        val p = paymentUseCase.cancel(im.bigs.pg.application.payment.port.`in`.CancelCommand(id, req.cancelAmount, req.reason))
        return ResponseEntity.ok(PaymentResponse.from(p))
    }
```

---

## 3.5 핵심 구현 코드 발췌

### 커서 기반 페이지 쿼리 (JPA)
```kotlin
@Query(
    """
    select p from PaymentEntity p
    where (:partnerId is null or p.partnerId = :partnerId)
      and (:status is null or p.status = :status)
      and (:fromAt is null or p.createdAt >= :fromAt)
      and (:toAt is null or p.createdAt < :toAt)
      and (
            (:cursorCreatedAt is null and :cursorId is null)
         or (p.createdAt < :cursorCreatedAt)
         or (p.createdAt = :cursorCreatedAt and p.id < :cursorId)
      )
    order by p.createdAt desc, p.id desc
    """,
)
fun pageBy(
    @Param("partnerId") partnerId: Long?,
    @Param("status") status: String?,
    @Param("fromAt") fromAt: Instant?,
    @Param("toAt") toAt: Instant?,
    @Param("cursorCreatedAt") cursorCreatedAt: Instant?,
    @Param("cursorId") cursorId: Long?,
    org: org.springframework.data.domain.Pageable,
): List<PaymentEntity>
```

### limit+1 로딩 및 nextCursor 생성
```kotlin
val pageSize = query.limit
val list = repo.pageBy(
    partnerId = query.partnerId,
    status = query.status?.name,
    fromAt = query.from?.toInstant(ZoneOffset.UTC),
    toAt = query.to?.toInstant(ZoneOffset.UTC),
    cursorCreatedAt = query.cursorCreatedAt?.toInstant(ZoneOffset.UTC),
    cursorId = query.cursorId,
    org = PageRequest.of(0, pageSize + 1),
)
val hasNext = list.size > pageSize
val items = list.take(pageSize)
val last = items.lastOrNull()
return PaymentPage(
    items = items.map { it.toDomain() },
    hasNext = hasNext,
    nextCursorCreatedAt = last?.createdAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
    nextCursorId = last?.id,
)
```

### 커서 인코딩/디코딩 (Base64 URL-safe)
```kotlin
private fun encodeCursor(createdAt: Instant?, id: Long?): String? {
    if (createdAt == null || id == null) return null
    val raw = "${createdAt.toEpochMilli()}:$id"
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
}

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
```

### API 응답 필드 (nextCursor/hasNext 포함)
```kotlin
data class PaymentsResponse(
    val items: List<PaymentItemDto>,
    val summary: SummaryDto,
    val nextCursor: String?,
    val hasNext: Boolean,
)
```

---

## 3.6 추가 제휴사 연동(어댑터) 및 전략 선택

### 런타임 전략 선택 (파트너 기반)
```26:45:modules/application/src/main/kotlin/im/bigs/pg/application/payment/service/PaymentService.kt
        val pgClient =
            pgClients.firstOrNull { it.supports(partner.id) }
                ?: throw IllegalStateException("No PG client supports partner ${partner.id}")

        val approve =
            pgClient.approve(
                PgApproveRequest(
                    partnerId = partner.id,
                    amount = command.amount,
                    cardBin = command.cardBin,
                    cardLast4 = command.cardLast4,
                    productName = command.productName,
                ),
            )
```

### 어댑터 등록과 우선순위/지원 범위
```21:33:modules/external/pg-client/src/main/kotlin/im/bigs/pg/external/pg/TestPgClient.kt
@Component
@Order(1)
class TestPgClient(
    private val rest: RestTemplate,
    @Value("${'$'}{pg.test.base-url:${'$'}{PG_BASE_URL:http://localhost:18080}}") private val baseUrl: String,
    @Value("${'$'}{pg.test.api-key:${'$'}{PG_API_KEY:test-api-key}}") private val apiKey: String,
): PgClientOutPort {
    override fun supports(partnerId: Long): Boolean = true
```

```16:21:modules/external/pg-client/src/main/kotlin/im/bigs/pg/external/pg/MockPgClient.kt
@Component
@org.springframework.core.annotation.Order(2)
class MockPgClient : PgClientOutPort {
    override fun supports(partnerId: Long): Boolean = partnerId % 2L == 1L
```

---

## 3.7 오픈API 문서화 및 운영지표(Actuator)

### 의존성 추가 (springdoc + actuator)
```18:21:modules/bootstrap/api-payment-gateway/build.gradle.kts
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
```

### 설정 (springdoc)
```18:23:modules/bootstrap/api-payment-gateway/src/main/resources/application.yml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    # Use default path (/swagger-ui/index.html); leaving path unset to avoid 404s
```

---

## 3.8 MariaDB 전환(docker-compose) 및 초기 스키마 적용

### Docker Compose (MariaDB 서비스 + 앱 연동)
```1:13:docker-compose.yml
services:
  mariadb:
    image: mariadb:10.11
    container_name: mariadb
    restart: always
    environment:
      MARIADB_ROOT_PASSWORD: root-pass
      MARIADB_DATABASE: appdb
      MARIADB_USER: appuser
      MARIADB_PASSWORD: app-pass
    ports:
      - "3306:3306"
```

```44:47:docker-compose.yml
      SPRING_DATASOURCE_URL: jdbc:mariadb://mariadb:3306/appdb
      SPRING_DATASOURCE_USERNAME: appuser
      SPRING_DATASOURCE_PASSWORD: app-pass
```

### 애플리케이션 데이터소스/초기 스키마 로드
```4:9:modules/bootstrap/api-payment-gateway/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/appdb?useSSL=false&allowPublicKeyRetrieval=true
    username: appuser
    password: app-pass
    driver-class-name: org.mariadb.jdbc.Driver
```

```10:16:modules/bootstrap/api-payment-gateway/src/main/resources/application.yml
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  jpa:
    hibernate:
      ddl-auto: none
```

> 주: 마이그레이션 도구는 간소화를 위해 스키마 초기화(`spring.sql.init`)로 대체했습니다. 면접 상황에서는 Flyway/Liquibase 적용 지점은 위 데이터소스 설정을 기준으로 소개하시면 됩니다.

---

## 4. 빠른 검증 시나리오

**Swagger UI (문서 미리보기)**: [https://hyeonji826.github.io/backend-test-v1/](https://hyeonji826.github.io/backend-test-v1/)

아래 값 그대로 사용하면 수수료 2.5% 기준으로 손익 계산이 딱 떨어지며, 목록·취소·중복키까지 한 번에 검증 가능.

### 4.1 승인 생성 (성공)

**넣을 숫자**
- partnerId=1
- amount=20000
- cardBin="111122", cardLast4="3344"
- Idempotency-Key="demo-001"
- productName="demo"

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
  -d '{"partnerId":1,"amount":20000,"cardBin":"111122","cardLast4":"3344","productName":"demo"}'
```

**예상 결과 (요지)**
```json
{
  "id": 1,
  "partnerId": 1,
  "amount": 20000,
  "appliedFeeRate": 0.025,
  "feeAmount": 500,
  "netAmount": 19500,
  "cardLast4": "3344",
  "approvalCode": "TESTPG-12345",
  "status": "APPROVED"
}
```

### 4.2 목록/통계 확인 (커서 nextCursor)

**넣을 숫자**
- partnerId=1
- status=APPROVED
- limit=5

```bash
curl "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=5"
```

**예상 결과 (요지)**
```json
{
  "items": [
    {
      "id": 1,
      "amount": 20000,
      "appliedFeeRate": 0.025,
      "feeAmount": 500,
      "netAmount": 19500,
      "status": "APPROVED"
    }
  ],
  "summary": { "count": 1, "totalAmount": "20000", "totalNetAmount": "19500" },
  "nextCursor": null,
  "hasNext": false
}
```

**다음 페이지 조회 (예시)**
```bash
curl "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=5&cursor={여기에_nextCursor_값}"
```

### 4.3 부분 취소 (5,000원)

**넣을 숫자**
- paymentId=1
- cancelAmount=5000
- reason="부분 환불"

```bash
curl -X POST http://localhost:8080/api/v1/payments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason":"부분 환불","cancelAmount":5000}'
```

**예상 결과 (요지)**
```json
{
  "id": 1,
  "status": "PARTIALLY_CANCELED",
  "canceledAmount": 5000
}
```

### 4.4 전체 취소

**넣을 숫자**
- paymentId=1
- reason="고객 요청"

```bash
curl -X POST http://localhost:8080/api/v1/payments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason":"고객 요청"}'
```

**예상 결과 (요지)**
```json
{ "id": 1, "status": "CANCELED" }
```

### 4.5 Idempotency-Key 중복 (409)

**넣을 숫자**
- 같은 바디
- 같은 키 Idempotency-Key="dup-001"

```bash
# 1회차 (성공)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: dup-001" \
  -d '{"partnerId":1,"amount":10000,"cardLast4":"1234","productName":"test"}'

# 2회차 (중복 -> 409)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: dup-001" \
  -d '{"partnerId":1,"amount":10000,"cardLast4":"1234","productName":"test"}'
```

**예상 결과 (요지)**
```json
{ "code": 409, "errorCode": "IDEMPOTENCY_CONFLICT" }
```

### 4.6 입력 검증 실패 (400/422)

**넣을 숫자**
- 음수 금액 amount=-1000
- 잘못된 자리수 cardLast4="123"

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: invalid-amount" \
  -d '{"partnerId":1,"amount":-1000,"cardLast4":"1234","productName":"test"}'

curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: invalid-card" \
  -d '{"partnerId":1,"amount":10000,"cardLast4":"123","productName":"test"}'
```

**예상 결과 (요지)**
```json
{ "code": 422, "errorCode": "VALIDATION_ERROR" }
```

### 4.7 존재하지 않는 파트너 (404)

**넣을 숫자**
- partnerId=999999

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: invalid-partner" \
  -d '{"partnerId":999999,"amount":10000,"cardLast4":"1234","productName":"test"}'
```

**예상 결과 (요지)**
```json
{ "code": 404, "errorCode": "NOT_FOUND" }
```

### 4.8 수수료 정책 비교 (파트너별)

**넣을 숫자**
- TEST 파트너: partnerId=1 → 2.5%
- NEW 파트너: partnerId=2 → 3.0%
- 동일 금액 amount=20000

```bash
# TEST (2.5%)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: fee-test" \
  -d '{"partnerId":1,"amount":20000,"cardLast4":"1111","productName":"TEST 파트너"}'

# NEW (3.0%)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" -H "Idempotency-Key: fee-new" \
  -d '{"partnerId":2,"amount":20000,"cardLast4":"2222","productName":"NEW 파트너"}'
```

**예상 결과 (요지)**
```json
# TEST
{ "appliedFeeRate": 0.025, "feeAmount": 500, "netAmount": 19500 }

# NEW
{ "appliedFeeRate": 0.03,  "feeAmount": 600, "netAmount": 19400 }
```

### 4.9 WireMock 요청 로그
```bash
curl http://localhost:18080/__admin/requests | jq '.requests[] | {method: .request.method, url: .request.url, status: .response.status}'
```

**예상 확인 포인트**: 승인/취소 호출이 200/201/204/422 등으로 기록되는지, URL 경로/메서드가 시나리오와 일치하는지

---

## 5. 에러 재현 (환경 변수 기반)

| 케이스 | 재현 방법 | 기대 응답 |
|--------|------------|------------|
| PG 실패 (422) | 컨테이너에 `PG_API_KEY=fail-api-key` 설정 후 취소 호출 | 422 |
| 인증 누락 (401) | `PG_API_KEY` 비우거나 `bad-key` | 401 |
| Idempotency 중복 | 동일 키로 승인 2회 | 409 |
| 검증 실패 | 음수 금액·자리수 오류 | 400/422 |

**공통 에러 포맷 (예시)**
```json
{ "code": 422, "errorCode": "INSUFFICIENT_LIMIT", "message": "...", "referenceId": "ref-xyz" }
```

---

## 6. 빌드·실행·코드 스타일

### 빌드/테스트/실행
```bash
./gradlew build
./gradlew test
./gradlew :modules:bootstrap:api-payment-gateway:bootRun
```

### E2E 초기화
```bash
powershell -ExecutionPolicy Bypass -File .\scripts\docker-e2e.ps1
```

**포트**: 8080

### 스타일 체크/정렬
```bash
./gradlew ktlintCheck
./gradlew ktlintFormat
```

---

## 기술 스택

- **언어**: Kotlin 1.9.25, Java 21
- **프레임워크**: Spring Boot 3.4.4
- **데이터베이스**: MariaDB 10.11
- **빌드**: Gradle (Kotlin DSL)
- **컨테이너**: Docker Compose
- **모킹**: WireMock 3.9.1
- **문서화**: SpringDoc OpenAPI
