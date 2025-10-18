# 🧾 API Payment Gateway (사전 과제)

> **과제 주제:** 나노바나나 페이먼츠 결제 도메인 서버  
> **목표:** 결제 승인/취소, 수수료 정책, 커서 기반 페이지네이션 구현 및 테스트 완성

---

## 📌 1. 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | API Payment Gateway |
| **핵심 기능** | 결제 승인/취소 (WireMock), 파트너별 수수료 정책, 커서 기반 페이지네이션, Idempotency-Key 중복 방지 |
| **언어/환경** | Kotlin + Spring Boot 3.x |
| **DB / Infra** | MariaDB, WireMock, Docker Compose |
| **빌드 툴** | Gradle Wrapper (Kotlin DSL) |

---

## ⚙️ 2. 실행 환경

- **Java** 21 (Eclipse Temurin)  
- **Docker** / **Docker Compose**  
- **Spring Boot 3.x (Kotlin)**  
- **MariaDB**, **WireMock**

---

## 🔧 3. 환경 변수 설정

| Key | 예시값 | 설명 |
|-----|--------|------|
| `SPRING_PROFILES_ACTIVE` | `local-docker` | 도커 네트워크 프로파일 |
| `PG_BASE_URL` | `http://wiremock:8080` | 외부 PG 모킹 Base URL |
| `PG_API_KEY` | `test-api-key` | PG 호출 API Key<br>(실패 유도 시 `fail-api-key` 사용) |

> 💡 모든 스크립트와 코드가 위 키 이름을 기반으로 동작합니다.

---

## 🗄️ 4. DB 스키마 / 시드

- `scripts/docker-e2e.ps1` 실행 시 `sql/scheme.sql` 자동 적용  
- 기본 시드 데이터:
  - 파트너: **TEST**, **NEW**
  - 수수료 정책: TEST=2.5%, NEW=3.0%

---

## 🚀 5. API 요약

### ✅ 결제 승인 생성 (`POST /api/v1/payments`)

**Headers**
Idempotency-Key: <string>

pgsql
코드 복사

**Request**
```json
{
  "partnerId": 1,
  "amount": 20000,
  "cardBin": "111122",
  "cardLast4": "3344",
  "productName": "demo"
}
```

**Response**
```json
{
  "id": 123,
  "partnerId": 1,
  "amount": 20000,
  "appliedFeeRate": 0.025,
  "feeAmount": 500,
  "netAmount": 19500,
  "cardLast4": "3344",
  "approvalCode": "TESTPG-12345",
  "approvedAt": "2025-01-01T00:00:00Z",
  "status": "APPROVED"
}
📊 결제 조회 (커서 기반 + 통계)
Request

bash
코드 복사
GET /api/v1/payments?partnerId=1&status=APPROVED&limit=5&cursor=<token>
Response

json
코드 복사
{
  "items": [{ "id": 10, "amount": 20000, "status": "APPROVED" }],
  "summary": { "count": 10, "totalAmount": 200000, "totalNetAmount": 195000 },
  "nextCursor": "eyJjcmVh...",
  "hasNext": true
}
❌ 결제 취소 (전체/부분)
Request

bash
코드 복사
POST /api/v1/payments/{id}/cancel
전체 취소

json
코드 복사
{ "reason": "user_request" }
부분 취소

json
코드 복사
{ "reason": "partial_refund", "cancelAmount": 5000 }
🧩 6. 외부 PG 모킹 (WireMock)
Endpoint	용도	Header
POST /api/v1/pay/credit-card	승인	API-KEY
POST /api/v1/pay/cancel	취소	API-KEY

애플리케이션은 도커 네트워크 내 http://wiremock:8080 을 사용합니다.

🧪 7. 빠른 시연 스크립트 (PowerShell)
powershell
코드 복사
# 1) 결제 승인
$body = @{ partnerId=1; amount=20000; cardBin="111122"; cardLast4="3344"; productName="demo" } | ConvertTo-Json
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/payments `
  -ContentType application/json `
  -Headers @{ "Idempotency-Key"="demo-001" } `
  -Body $body

# 2) 결제 조회
Invoke-RestMethod "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=5"

# 3) 결제 취소
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/payments/1/cancel `
  -ContentType application/json `
  -Body (@{ reason="user_request" } | ConvertTo-Json)

# 4) WireMock 요청 로그 확인
Invoke-RestMethod http://localhost:18080/__admin/requests | ConvertTo-Json -Depth 6
🧰 8. 에러 재현 가이드
케이스	재현 방법	기대 응답
PG 실패 (422)	PG_API_KEY=fail-api-key 로 컨테이너 실행 후 취소	422 JSON
인증 누락 (401)	PG_API_KEY 비움 또는 bad-key 설정	401 JSON
입력 검증 실패	음수 금액, 빈 필드, 잘못된 자리수	400/422
Idempotency 중복	동일 Idempotency-Key 로 승인 2회	409 JSON

📘 9. Swagger / OpenAPI 문서
항목	주소
Swagger UI	http://localhost:8080/swagger-ui.html
OpenAPI Spec	http://localhost:8080/v3/api-docs

Gradle 설정

kotlin
코드 복사
dependencies {
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
}
application.yml

yaml
코드 복사
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
🚨 10. 에러 응답 예시 (JSON)
코드	유형	예시 메시지
400 / 422	VALIDATION_ERROR	"amount must be >= 1, cardLast4 must be 4 digits"
401	UNAUTHORIZED	"API-KEY missing or invalid"
404	NOT_FOUND	"Partner not found: id=999999"
409	IDEMPOTENCY_CONFLICT	"Duplicate request with same Idempotency-Key"
422 (PG)	OVER_CANCEL	"Cancel amount exceeds approved"

공통 응답 형식

json
코드 복사
{
  "code": 422,
  "errorCode": "INSUFFICIENT_LIMIT",
  "message": "Credit limit exceeded",
  "referenceId": "ref-xyz"
}
🧱 11. 아키텍처 구조 요약
bash
코드 복사
modules/
 ├── domain/                  # 순수 도메인 모델 (FeePolicy, Payment)
 ├── application/             # 유스케이스, 서비스, Port
 ├── infrastructure/
 │    └── persistence/        # JPA 엔티티/리포지토리, 페이징/통계 어댑터
 ├── external/pg-client/      # PG 연동 어댑터(Mock/TestPay)
 └── bootstrap/api-payment-gateway/  # Spring Boot Entry
하드코드 수수료(3%+100원) → 정책 테이블 기반으로 리팩터링
도메인 계층은 프레임워크 의존 금지
헥사고널 아키텍처 (Ports & Adapters) 유지

✅ 12. 필수 요구사항 체크리스트
 결제 승인 / 취소 정상 동작

 수수료 정책 기반 계산

 커서 기반 페이지네이션 및 통계

 Idempotency-Key 중복 방지

 단위/통합 테스트 통과

🧩 13. 빌드 / 실행
bash
코드 복사
./gradlew build
./gradlew test
./gradlew :modules:bootstrap:api-payment-gateway:bootRun
기본 포트: 8080

코드 스타일 검사

bash
코드 복사
./gradlew ktlintCheck
자동 정렬

bash
코드 복사
./gradlew ktlintFormat
yaml
코드 복사
