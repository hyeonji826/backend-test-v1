# API Payment Gateway (사전 과제)

## 프로젝트 구성 사항

### 핵심 기능 구현
- 결제 승인/취소 API (WireMock 연동)
- Idempotency-Key 기반 중복 방지
- 파트너별 수수료 정책 적용 (정책 테이블 기반)
- 커서 기반 페이지네이션 및 통계 조회
- 헥사고널 아키텍처 (Port & Adapter 패턴)

### 개선사항 및 가산점
- 하드코드 수수료 → 정책 테이블 기반 동적 계산
- 다중 PG 지원 (전략 패턴 적용)
- SpringDoc OpenAPI 문서화
- MariaDB Docker Compose 환경 구성 (외부 DB 연동)
- Spring Boot Actuator 운영 모니터링
- 보안 로깅 (민감정보 제외)
- PowerShell E2E 테스트 스크립트  

---

## 빠른 실행

```bash
# 1. 전체 환경 실행
docker-compose up -d
powershell -ExecutionPolicy Bypass -File .\scripts\docker-e2e.ps1

# 2. API 테스트
curl http://localhost:8080/actuator/health
```

**Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## API 엔드포인트

### 결제 승인 생성
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{
    "partnerId": 1,
    "amount": 20000,
    "cardBin": "111122",
    "cardLast4": "3344",
    "productName": "테스트 상품"
  }'
```

### 결제 조회 (통계 + 커서 페이지네이션)
```bash
curl "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=5"
```

### 결제 취소
```bash
# 전체 취소
curl -X POST http://localhost:8080/api/v1/payments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason": "고객 요청"}'

# 부분 취소
curl -X POST http://localhost:8080/api/v1/payments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason": "부분 환불", "cancelAmount": 5000}'
```

---

## 아키텍처 구조

```
modules/
├── domain/                  # 순수 도메인 모델 (프레임워크 의존성 없음)
├── application/             # 유스케이스, Port 인터페이스
├── infrastructure/persistence/  # JPA 엔티티, 어댑터
├── external/pg-client/      # PG 연동 (TestPg, MockPg)
└── bootstrap/api-payment-gateway/  # Spring Boot 진입점
```

**핵심 설계 원칙**
- **도메인 순수성**: 프레임워크 의존성 완전 제거
- **의존성 역전**: Port & Adapter 패턴
- **확장성**: 전략 패턴으로 다중 PG 지원

---

## 검증용 테스트 시나리오

### 기본 테스트 시나리오

#### 1단계: 결제 승인 생성
```bash
# 정상 결제 승인
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-payment-001" \
  -d '{
    "partnerId": 1,
    "amount": 20000,
    "cardBin": "111122",
    "cardLast4": "3344",
    "productName": "테스트 상품"
  }'
```

**예상 응답:**
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
  "approvedAt": "2025-01-01T00:00:00Z",
  "status": "APPROVED",
  "createdAt": "2025-01-01T00:00:00Z"
}
```

#### 2단계: 결제 조회 (통계 포함)
```bash
# 기본 조회
curl "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=5"

# 커서 페이지네이션
curl "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=3&cursor=eyJjcmVhdGVkQXQiOiIyMDI1LTAxLTAxVDAwOjAwOjAwWiIsImlkIjoxfQ"
```

**예상 응답:**
```json
{
  "items": [
    {
      "id": 1,
      "partnerId": 1,
      "amount": 20000,
      "appliedFeeRate": 0.025,
      "feeAmount": 500,
      "netAmount": 19500,
      "cardLast4": "3344",
      "approvalCode": "TESTPG-12345",
      "approvedAt": "2025-01-01T00:00:00Z",
      "status": "APPROVED",
      "createdAt": "2025-01-01T00:00:00Z",
      "updatedAt": "2025-01-01T00:00:00Z"
    }
  ],
  "summary": {
    "count": 1,
    "totalAmount": "20000",
    "totalNetAmount": "19500"
  },
  "nextCursor": null,
  "hasNext": false
}
```

#### **3단계: 결제 취소**
```bash
# 전체 취소
curl -X POST http://localhost:8080/api/v1/payments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "고객 요청"
  }'

# 부분 취소
curl -X POST http://localhost:8080/api/v1/payments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "부분 환불",
    "cancelAmount": 5000
  }'
```

### **에러 케이스 테스트**

#### **Idempotency-Key 중복 테스트**
```bash
# 동일한 키로 두 번 요청
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: duplicate-test" \
  -d '{"partnerId": 1, "amount": 10000, "cardLast4": "1234", "productName": "test"}'

# 동일한 키로 재요청 (409 에러 예상)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: duplicate-test" \
  -d '{"partnerId": 1, "amount": 10000, "cardLast4": "1234", "productName": "test"}'
```

#### **입력 검증 실패 테스트**
```bash
# 음수 금액 (400 에러)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: invalid-amount" \
  -d '{"partnerId": 1, "amount": -1000, "cardLast4": "1234", "productName": "test"}'

# 잘못된 카드번호 (400 에러)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: invalid-card" \
  -d '{"partnerId": 1, "amount": 10000, "cardLast4": "123", "productName": "test"}'
```

#### **존재하지 않는 파트너 (404 에러)**
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: invalid-partner" \
  -d '{"partnerId": 999999, "amount": 10000, "cardLast4": "1234", "productName": "test"}'
```

### 📊 **수수료 정책 테스트**

#### **다른 파트너로 결제 (수수료 차이 확인)**
```bash
# TEST 파트너 (2.5% 수수료)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-partner-fee" \
  -d '{"partnerId": 1, "amount": 20000, "cardLast4": "1111", "productName": "TEST 파트너"}'

# NEW 파트너 (3.0% 수수료)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: new-partner-fee" \
  -d '{"partnerId": 2, "amount": 20000, "cardLast4": "2222", "productName": "NEW 파트너"}'
```

### 🐳 **Docker 환경 테스트**

#### **전체 환경 실행**
```bash
# 1. Docker Compose 실행
docker-compose up -d

# 2. 스키마 및 시드 데이터 적용
powershell -ExecutionPolicy Bypass -File .\scripts\docker-e2e.ps1

# 3. API 테스트
curl http://localhost:8080/actuator/health
```

#### **WireMock 요청 로그 확인**
```bash
# WireMock 요청 기록 확인
curl http://localhost:18080/__admin/requests | jq '.requests[] | {method: .request.method, url: .request.url, status: .response.status}'
```

### 📋 **면접관 체크리스트**

#### **기본 기능 검증**
- [ ] 결제 승인 생성 (200 OK)
- [ ] 수수료 계산 정확성 (TEST: 2.5%, NEW: 3.0%)
- [ ] 결제 조회 및 통계 (summary 정확성)
- [ ] 커서 페이지네이션 (nextCursor, hasNext)
- [ ] 결제 취소 (전체/부분)

#### **에러 처리 검증**
- [ ] Idempotency-Key 중복 (409 Conflict)
- [ ] 입력 검증 실패 (400/422)
- [ ] 존재하지 않는 파트너 (404)
- [ ] 잘못된 취소 금액 (422)

#### **아키텍처 검증**
- [ ] Swagger UI 접근 가능
- [ ] WireMock 연동 확인
- [ ] 데이터베이스 스키마 적용
- [ ] 로그 레벨 설정 (DEBUG)

### 🚀 **빠른 시연 스크립트 (PowerShell)**

```powershell
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
```

---

## 8. 에러 재현 가이드

| 케이스 | 재현 방법 | 기대 응답 |
|--------|------------|------------|
| **PG 실패 (422)** | `PG_API_KEY=fail-api-key` 로 컨테이너 실행 후 취소 | 422 JSON |
| **인증 누락 (401)** | `PG_API_KEY` 비움 또는 `bad-key` 설정 | 401 JSON |
| **입력 검증 실패** | 음수 금액, 빈 필드, 잘못된 자리수 | 400/422 |
| **Idempotency 중복** | 동일 `Idempotency-Key` 로 승인 2회 | 409 JSON |

---

## 9. Swagger / OpenAPI 문서

| 항목 | 주소 |
|------|------|
| **Swagger UI** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| **OpenAPI Spec** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |

**Gradle 설정**
```kotlin
dependencies {
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
}
```

**application.yml**
```yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
```

---

## 10. 에러 응답 예시 (JSON)

| 코드 | 유형 | 예시 메시지 |
|------|------|--------------|
| 400 / 422 | VALIDATION_ERROR | `"amount must be >= 1, cardLast4 must be 4 digits"` |
| 401 | UNAUTHORIZED | `"API-KEY missing or invalid"` |
| 404 | NOT_FOUND | `"Partner not found: id=999999"` |
| 409 | IDEMPOTENCY_CONFLICT | `"Duplicate request with same Idempotency-Key"` |
| 422 (PG) | OVER_CANCEL | `"Cancel amount exceeds approved"` |

**공통 응답 형식**
```json
{
  "code": 422,
  "errorCode": "INSUFFICIENT_LIMIT",
  "message": "Credit limit exceeded",
  "referenceId": "ref-xyz"
}
```

---

## 11. 아키텍처 구조 요약

```bash
modules/
 ├── domain/                  # 순수 도메인 모델 (FeePolicy, Payment)
 ├── application/             # 유스케이스, 서비스, Port
 ├── infrastructure/
 │    └── persistence/        # JPA 엔티티/리포지토리, 페이징/통계 어댑터
 ├── external/pg-client/      # PG 연동 어댑터(Mock/TestPay)
 └── bootstrap/api-payment-gateway/  # Spring Boot Entry
```

> 하드코드 수수료(3%+100원) → 정책 테이블 기반으로 리팩터링  
> 도메인 계층은 프레임워크 의존 금지  
> 헥사고널 아키텍처 (Ports & Adapters) 유지

---

## 빌드 및 실행

```bash
./gradlew build
./gradlew test
./gradlew :modules:bootstrap:api-payment-gateway:bootRun
powershell -ExecutionPolicy Bypass -File .\scripts\docker-e2e.ps1

```

> 기본 포트: **8080**

**코드 스타일 검사**
```bash
./gradlew ktlintCheck
```

**자동 정렬**
```bash
./gradlew ktlintFormat
```
