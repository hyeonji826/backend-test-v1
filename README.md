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

**Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 3. API 엔드포인트 (명령만)

### 결제 승인 생성
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{"partnerId":1,"amount":20000,"cardBin":"111122","cardLast4":"3344","productName":"테스트 상품"}'
```

### 결제 목록 (통계+커서)
```bash
curl "http://localhost:8080/api/v1/payments?partnerId=1&status=APPROVED&limit=5"
```

### 결제 취소 (전체/부분)
```bash
# 전체
curl -X POST http://localhost:8080/api/v1/payments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason":"고객 요청"}'

# 부분 (5,000원)
curl -X POST http://localhost:8080/api/v1/payments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason":"부분 환불","cancelAmount":5000}'
```

---

## 4. 빠른 검증 시나리오

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

### 4.2 목록/통계 확인 (커서 X)

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
  "hasNext": false
}
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
