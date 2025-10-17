# 백엔드 사전 과제 – 결제 도메인 서버

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