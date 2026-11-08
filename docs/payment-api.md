# 결제 도메인 API 명세

레시픽(Recipick) · 결제 도메인(결제 · 환불 · 웹훅 · 빌링키 · 포인트 · 관리자 결제) · 기준: `dev` `9172f6d` 구현(2026-11-04)

## 공통

- Swagger UI(`/api/swagger-ui`, OpenAPI 문서 `/api/docs`)는 코드에서 자동 생성한 엔드포인트 목록을 보여줍니다. 이 문서는 그 위의 업무 계약(에러 코드 · 멱등 키 · 상태별 응답 · 처리 순서)을 정리합니다.

### 인증

| 구분 | 경로 | 권한 | 헤더 |
| --- | --- | --- | --- |
| 공개 API | `/api/config/portone` | PUBLIC | 없음 |
| 웹훅 | `/api/webhooks/portone` | WEBHOOK | `webhook-id`, `webhook-timestamp`, `webhook-signature` (PortOne 서명) |
| 회원 API | `/api/payments`, `/api/billing-keys`, `/api/points` | MEMBER | `Authorization: Bearer {회원 accessToken}` |
| 관리자 API | `/api/admin/payments`, `/api/admin/members/{memberId}/points`, `/api/admin/membership-payment-attempts`, `/api/admin/membership-refunds` | SUPER_ADMIN | `Authorization: Bearer {관리자 accessToken}` |

- 회원 ID · 관리자 ID는 토큰에서 꺼내며 경로나 본문으로 받지 않습니다. 관리자 포인트 조회의 `{memberId}`는 조회 대상 회원입니다.
- 공개 API와 웹훅은 JWT 필터를 거치지 않습니다. `Authorization` 헤더를 보내도 읽지 않습니다. 웹훅은 JWT 대신 PortOne 서명으로 인증합니다.
- `/api/admin/**`은 보안 설정에서 SUPER_ADMIN · MART_ADMIN 모두에게 열려 있지만, 결제 도메인의 관리자 컨트롤러는 모두 클래스 단위 `@PreAuthorize("hasRole('SUPER_ADMIN')")`로 SUPER_ADMIN 전용입니다.
- 토큰이 없으면 401 `AUTH-001`, 형식 오류 · 위조는 401 `AUTH-002`, 만료는 401 `AUTH-003`, 탈퇴한 회원의 토큰은 401 `AUTH-011`입니다. 회원 토큰으로 관리자 API를 호출하거나 그 반대면 403 `AUTH-004`, MART_ADMIN이 결제 관리자 API를 호출하면 403 `AUTH-006 (SUPER_ADMIN_ONLY)`입니다. 아래 API별 예외 표에서는 이 공통 인증 오류를 생략합니다.
- `/api/**` 요청은 IP당 1분 120회로 제한되며 넘으면 429 `COMMON-012`입니다. 웹훅(`/api/webhooks/**`)은 제한하지 않습니다.

### 응답 형식

성공

```json
{
  "success": true,
  "code": "OK",
  "message": "결제가 확정되었습니다.",
  "data": { }
}
```

실패

```json
{
  "success": false,
  "code": "PAYMENT-001",
  "message": "결제 정보를 찾을 수 없습니다.",
  "errors": [],
  "timestamp": "2026-11-04T21:30:00+09:00"
}
```

- 성공 응답의 `code`는 200 · 202를 포함해 항상 `OK`입니다. `message`는 API마다 다르며(아래 각 절의 예시), 별도 문구가 없는 API는 `요청이 처리되었습니다.`입니다.
- `errors`는 입력값 검증 실패(`COMMON-001`)일 때만 `[{ "field": "reason", "reason": "..." }]`로 채워지고, 그 밖에는 빈 배열입니다.
- 본문 JSON이 깨졌거나 타입이 맞지 않으면 400 `COMMON-002`, 필수 헤더 `Idempotency-Key`가 없으면 400 `COMMON-007`입니다. 쿼리 · 경로 값의 형식 오류는 API마다 바인딩 방식이 달라 응답 코드가 다릅니다. 특히 `@Valid @ModelAttribute`로 받는 관리자 검색 조건은 각 API의 예외처리 표(`COMMON-001`)를 따릅니다.
- 비관적 락 대기 시간 초과는 409 `COMMON-010`입니다.
- PortOne 오류 응답 · 연결 실패 · 응답 해석 실패는 500 `COMMON-008`, PortOne 응답 지연(timeout)은 500 `COMMON-009`입니다.

### 값 형식

- 시각: 한국 시간 ISO-8601, 초 단위 (`2026-11-04T21:30:00+09:00`). 서버는 `LocalDateTime`(Asia/Seoul)으로 저장하고 `JacksonConfig`가 `+09:00`을 붙여 내보냅니다.
- 날짜(포인트 만료일): `yyyy-MM-dd` (`2027-11-04`)
- 금액 · 포인트: 원 단위 정수(Long)
- enum: UPPER_SNAKE_CASE, JSON 필드: camelCase
- PortOne 상품 결제 ID는 `pay_` + UUID, 멤버십 결제 ID는 `mpay_` + 하이픈 없는 UUID입니다.

### 목록(페이지) 규칙

- 회원 목록(결제 목록 · 포인트 적립 내역 · 포인트 이력)과 관리자 회원 포인트 이력: `page`(0부터, 기본 0), `size`(기본 20, 최대 100. 넘기면 100으로 잘림). `sort`는 받아도 무시하고 항상 `createdAt` 내림차순, 같으면 `id` 내림차순(최신순)입니다.
- 관리자 결제 목록 · 멤버십 결제 시도 목록은 `page` · `size`를 Bean Validation으로 검사합니다(`page` 0 이상, `size` 1~100, 벗어나면 400 `COMMON-001`). 정렬 규칙은 13절 · 17절을 따릅니다.
- 응답 `data`: `{ content, page, size, totalElements, totalPages }`

### 상태 값

| 구분 | 값 | 설명 |
| --- | --- | --- |
| 결제 상태 `paymentStatus` | IN_PROGRESS | 결제 대기(주문 생성 직후, 5분 유효) |
|  | PAID | 결제 완료 |
|  | FAILED | 결제 전 종료(PG 실패 · 만료 · 결제 전 취소 · 금액 불일치) |
|  | CANCEL_REQUESTED | 결제 후 취소 · 환불 진행 중(PortOne 결과 대기) |
|  | CANCELED | 결제 후 전액 취소 완료 |
| 결제 수단 `paymentMethod` | CARD | PortOne 카드 결제 |
|  | POINTS_ONLY | 외부 결제금액 0원. PG를 거치지 않고 주문 생성 트랜잭션에서 바로 PAID |
| 실패 사유 `failureReason` | EXPIRED | 결제 대기 5분 만료(또는 PG 결제 완료 시각이 만료 시각 이후) |
|  | CANCELED_BEFORE_PAY | 결제 전 회원 취소 |
|  | AMOUNT_MISMATCH | PG 결제 금액이 결제 금액과 다름 |
|  | PG_FAILED | PG가 결제 실패를 확정 |
| 환불 상태 `refundStatus` · `latestRefundStatus` | REQUESTED | 환불 요청됨(PortOne 결과 대기) |
|  | COMPLETED | PortOne 취소 성공 확인 |
|  | FAILED | PortOne이 취소 실패를 명시했거나 요청을 거절 |
| 환불 요청 주체 `requestedBy` | MEMBER | 회원 직접 취소 |
|  | SYSTEM | 만료 뒤 승인 · 금액 불일치 · 종료된 결제의 늦은 승인에 대한 자동 반환 |
|  | EXTERNAL | PortOne 콘솔 등 시스템 밖에서 일어난 전액 취소 |
|  | ADMIN | 마트 거절 환불 |
| 빌링키 상태 `status` | ACTIVE | 회원의 현재 자동결제 수단 |
|  | REPLACED | 새 카드로 교체된 이전 키 |
|  | REVOKED | PG가 사용 불가로 답한 키 |
|  | DELETE_REQUESTED | PortOne 삭제 요청을 기록한 상태 |
|  | DELETED | PortOne 삭제 확인 |
| 빌링키 등록 결과 `outcome` | REGISTERED | 새 키를 ACTIVE로 저장 |
|  | REPLACED | 이전 ACTIVE 키를 REPLACED로 바꾸고 새 키를 ACTIVE로 저장 |
|  | ALREADY_ACTIVE | 같은 키가 이미 ACTIVE. 변경 없음 |
|  | REACTIVATED | 내 REPLACED 키를 다시 ACTIVE로 |
| 적립 상태 `status`(포인트 적립 건) | PENDING | 적립 예정(배송완료 전) |
|  | COMPLETED | 적립 확정(사용 가능) |
|  | CANCELED | 배송완료 전 주문 종료로 적립 취소 |
|  | EXPIRED | 만료 소멸 |
|  | FORFEITED | 회원 탈퇴로 소멸 |
| 포인트 이력 `type` | EARN_PENDING | 적립 예정 생성 |
|  | EARN_COMPLETED | 적립 확정 |
|  | EARN_CANCELED | 적립 예정 취소 |
|  | USE_RESERVED | 주문에 사용 예약 |
|  | USE_CONFIRMED | 결제 성공으로 사용 확정 |
|  | RESTORED | 주문 종료로 사용분을 원래 적립 건에 복원 |
|  | EXPIRED | 만료 소멸 |
|  | WITHDRAWAL_FORFEITED | 회원 탈퇴 소멸 |
| 멤버십 결제 회차 `paymentStatus` | PENDING | 접수됨. 시도 진행 중이거나 곧 시작 |
|  | RETRY_WAITING | 갱신 실패 뒤 다음 시도 대기 |
|  | PAYMENT_METHOD_REQUIRED | 활성 빌링키가 없어 카드 등록 대기 |
|  | UNKNOWN | PG 결과 불명. 재조회로만 확정 |
|  | MANUAL_REVIEW | 자동 확정 불가, 운영 확인 |
|  | PAID / FAILED | 확정 |
| 멤버십 결제 시도 `status` | READY | 접수됨. 아직 PortOne을 부르지 않음 |
|  | DISPATCHING | 선점됨. PortOne 호출 중 |
|  | UNKNOWN | 호출 결과 불명. 같은 결제 ID로 재조회 |
|  | MANUAL_REVIEW | 재조회로도 확정 불가, 운영 확인 |
|  | PAID / FAILED | 확정 |
|  | CANCELED | 호출 전에 접은 시도 |
| 멤버십 환불 상태 `status` | REQUESTED / COMPLETED / FAILED | 환불 요청됨 / 완료 / 실패 |

- 멤버십 운영 확인 사유(`MembershipOpsReviewReason`, `MembershipRefundOpsReason`)는 13~21절의 관리자 API에서 다룹니다.

### API 목록

| # | API | Method / Path | 권한 |
| --- | --- | --- | --- |
| 1 | PortOne 설정 조회 | `GET /api/config/portone` | PUBLIC |
| 2 | 결제 확정 | `POST /api/payments/confirm` | MEMBER |
| 3 | 결제 취소(회원) | `POST /api/payments/{paymentId}/cancel` | MEMBER |
| 4 | 주문별 결제 조회 | `GET /api/payments/orders/{orderId}` | MEMBER |
| 5 | 결제 목록 조회 | `GET /api/payments` | MEMBER |
| 6 | PortOne 웹훅 수신 | `POST /api/webhooks/portone` | WEBHOOK |
| 7 | 빌링키 발급 요청 생성 | `POST /api/billing-keys/issue-requests` | MEMBER |
| 8 | 빌링키 등록 | `POST /api/billing-keys` | MEMBER |
| 9 | 내 결제수단 조회 | `GET /api/billing-keys/me` | MEMBER |
| 10 | 포인트 잔액 조회 | `GET /api/points` | MEMBER |
| 11 | 포인트 적립 내역 조회 | `GET /api/points/earnings` | MEMBER |
| 12 | 포인트 이력 조회 | `GET /api/points/history` | MEMBER |
| 13 | 관리자 결제 목록 조회 | `GET /api/admin/payments` | SUPER_ADMIN |
| 14 | 관리자 결제 상세 조회 | `GET /api/admin/payments/{paymentId}` | SUPER_ADMIN |
| 15 | 관리자 회원 포인트 조회 | `GET /api/admin/members/{memberId}/points` | SUPER_ADMIN |
| 16 | 관리자 회원 포인트 이력 조회 | `GET /api/admin/members/{memberId}/points/history` | SUPER_ADMIN |
| 17 | 관리자 멤버십 결제 시도 목록 조회 | `GET /api/admin/membership-payment-attempts` | SUPER_ADMIN |
| 18 | 관리자 멤버십 결제 시도 상세 조회 | `GET /api/admin/membership-payment-attempts/{attemptId}` | SUPER_ADMIN |
| 19 | 관리자 멤버십 결제 시도 재조회 | `POST /api/admin/membership-payment-attempts/{attemptId}/recheck` | SUPER_ADMIN |
| 20 | 관리자 멤버십 환불 재조회 | `POST /api/admin/membership-refunds/{refundId}/recheck` | SUPER_ADMIN |
| 21 | 관리자 멤버십 환불 재시도 | `POST /api/admin/membership-refunds/{refundId}/retry` | SUPER_ADMIN |

---

## 1. PortOne 설정 조회

### API: PortOne 설정 조회

### Method / Path

`GET /api/config/portone`

### 인증

- 인증 불필요(PUBLIC)

### Request

없음

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "storeId": "store-00000000-0000-0000-0000-000000000000",
    "channelKey": "channel-key-00000000-0000-0000-0000-000000000000"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| storeId | String | PortOne 상점 ID. 브라우저 SDK 결제창 호출에 사용 |
| channelKey | String | PortOne 채널 키. 브라우저 SDK 결제창 호출에 사용 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 500 | PAYMENT-010 (PORTONE_CONFIG_ERROR) | 서버에 storeId 또는 channelKey 설정이 비어 있음 |

### 비즈니스 규칙

- 브라우저에 공개해도 되는 값(상점 ID · 채널 키)만 내려줍니다. API Secret · 웹훅 시크릿은 응답에 넣지 않습니다.
- 설정이 하나라도 비어 있으면 값을 일부만 내려주지 않고 500으로 거절합니다. 로그에는 값이 아니라 "있음/없음"만 남깁니다.

---

## 2. 결제 확정

### API: 결제 확정

### Method / Path

`POST /api/payments/confirm`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

```json
{
  "orderId": 1001,
  "portonePaymentId": "pay_3f9c1e2a-5b7d-4c8e-9a1f-2b3c4d5e6f70"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Long | O | 주문 생성 응답의 주문 ID |
| portonePaymentId | String | O | 주문 생성 응답의 PortOne 결제 ID. 공백 불가 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "결제가 확정되었습니다.",
  "data": {
    "paymentId": 2001,
    "orderId": 1001,
    "paymentMethod": "CARD",
    "amount": 7000,
    "paymentStatus": "PAID",
    "paidAt": "2026-11-04T21:31:10+09:00"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| paymentId | Long | 결제 ID |
| orderId | Long | 주문 ID |
| paymentMethod | String | 결제 수단. 이 API는 CARD만 처리 |
| amount | Long | 결제 금액(결제 생성 시점 스냅샷) |
| paymentStatus | String | PAID |
| paidAt | String | PortOne이 확인한 결제 완료 시각 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | orderId 누락, portonePaymentId 누락 · 공백 |
| 400 | PAYMENT-002 (PAYMENT_NOT_PAID) | PortOne 결제가 아직 PAID가 아님(READY · PENDING · VIRTUAL_ACCOUNT_ISSUED 등), PAID인데 통화 · 금액 · 결제 완료 시각 중 하나가 없음 |
| 400 | PAYMENT-004 (PAYMENT_AMOUNT_MISMATCH) | PortOne 결제 금액이 결제 금액과 다름. 결제 실패 확정 + SYSTEM 환불 접수 |
| 404 | PAYMENT-001 (PAYMENT_NOT_FOUND) | 주문의 결제가 없음, 다른 회원의 주문, 0원 결제(POINTS_ONLY), portonePaymentId가 이 주문의 결제와 다름, PortOne에 결제가 없음(404), PortOne customData의 주문 ID가 이 주문과 다름 |
| 409 | PAYMENT-003 (PAYMENT_FAILED) | PortOne이 결제 실패(FAILED)를 확정. 결제 실패 확정 |
| 409 | PAYMENT-005 (PAYMENT_EXPIRED) | PortOne 결제 완료 시각이 결제 만료 시각보다 늦음. 결제 실패 확정 + SYSTEM 환불 접수 |
| 409 | PAYMENT-006 (PAYMENT_ALREADY_CLOSED) | 결제가 이미 FAILED · CANCELED로 종료됨(만료 큐 등이 먼저 닫음). FAILED 결제에 PortOne PAID가 확인되면 SYSTEM 환불 접수 |
| 409 | PAYMENT-007 (INVALID_PAYMENT_STATUS) | 결제가 CANCEL_REQUESTED, PortOne 결제가 CANCELED · PARTIAL_CANCELED |
| 409 | PAYMENT-013 (PAYMENT_CURRENCY_MISMATCH) | PortOne 결제 통화가 정확히 `KRW`가 아님. 상태 변경 없음, 운영 확인 |
| 409 | COMMON-010 (LOCK_ACQUISITION_TIMEOUT) | 결제 · 주문 · 포인트 · 재고 락 대기 시간 초과 |
| 500 | COMMON-008 (EXTERNAL_SERVICE_ERROR) | PortOne 조회 오류 응답 · 빈 응답 · 연결 실패 · 응답 해석 실패 |
| 500 | COMMON-009 (EXTERNAL_SERVICE_TIMEOUT) | PortOne 조회 timeout |

### 비즈니스 규칙

- 처리 순서(Facade는 트랜잭션을 걸지 않음):
  1. 선검증(각 Service의 짧은 읽기): 주문의 결제 존재 → 주문 소유 회원 일치 → CARD 결제이고 portonePaymentId가 일치. 셋 다 404 `PAYMENT-001`로 답해 남의 주문인지 · 번호가 틀린지를 구분해 주지 않습니다.
  2. PortOne `GET /payments/{paymentId}` 조회. 트랜잭션 · 락 밖에서 호출합니다.
  3. 상태 변경 트랜잭션: PAYMENT 행 비관적 락 → 잠금 아래에서 판정 → 판정별 반영 → 커밋.
  4. 커밋 뒤, SYSTEM 환불이 생길 수 있는 판정(금액 불일치 · 만료 · 이미 종료)이면 REQUESTED 상태의 SYSTEM 환불 실행을 Redis 지연 큐에 즉시 예약합니다. 예약 실패는 응답을 바꾸지 않고, 환불이 REQUESTED로 남아 1시간 주기 Fallback이 다시 등록합니다.
  5. 판정에 오류 코드가 있으면 그때 예외를 던집니다. 이미 커밋된 FAILED · 환불 행은 남습니다.
- 프론트가 보낸 결제 결과 · 금액은 믿지 않습니다. 승인 판단은 PortOne 조회 결과로만 합니다.
- 판정 순서(잠금 아래, `PaymentVerifier`):
  1. 이미 PAID → 변경 없이 200(멱등)
  2. FAILED 결제인데 PortOne이 PAID(늦은 승인) → customData 주문 ID 일치(`PAYMENT-001`) → 통화 KRW(누락 `PAYMENT-002`, 다른 통화 `PAYMENT-013`) → 금액 있음(누락 `PAYMENT-002`) 확인 뒤 3으로
  3. FAILED · CANCELED → `PAYMENT-006`, 그 밖에 IN_PROGRESS가 아니면 `PAYMENT-007`
  4. PortOne FAILED → `PAYMENT-003`, CANCELED · PARTIAL_CANCELED → `PAYMENT-007`(상태 변경 없음), PAID가 아니면 `PAYMENT-002`
  5. customData `{"orderId": …}`(정수만 인정)가 이 주문과 다르거나 없으면 `PAYMENT-001`
  6. 통화: null · 공백이면 `PAYMENT-002`(재조회 대상), 정확히 `"KRW"`가 아니면 `PAYMENT-013`. 금액 비교보다 먼저 봅니다.
  7. 금액: 없으면 `PAYMENT-002`, PAYMENT.amount 스냅샷과 다르면 `PAYMENT-004`
  8. 결제 완료 시각: 없으면 `PAYMENT-002`, 만료 시각(생성 + 5분)보다 늦으면 `PAYMENT-005`(같으면 통과). 서버 시각이 아니라 PG 결제 완료 시각으로 판정합니다.
  9. 모두 통과하면 승인
- 판정별 반영(한 트랜잭션, 락 순서 PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고):
  - 승인: 결제 PAID(PG 거래 ID · 결제 완료 시각 저장) → 주문 PAID · 마트 RECEIVED · 이력 → 사용 포인트 USE_CONFIRMED. 적립 예정은 배송완료까지 PENDING으로 둡니다.
  - PG 실패: 결제 FAILED(PG_FAILED), 주문 CANCELED(PAYMENT_FAILED, PG 실패 요약을 사유 상세에 저장), 사용 포인트 복원 · 적립 예정 취소, 재고 복구.
  - 금액 불일치: 결제 FAILED(AMOUNT_MISMATCH), PG 청구 금액으로 SYSTEM 환불(REQUESTED) 생성, 주문 CANCELED(AMOUNT_MISMATCH), 포인트 · 재고 복구.
  - 만료: 결제 FAILED(EXPIRED), PG 금액으로 SYSTEM 환불 생성, 주문 CANCELED(PAYMENT_TIMEOUT), 포인트 · 재고 복구.
  - 이미 종료(FAILED 결제의 늦은 PAID): 결제 FAILED 유지, 주문 · 포인트 · 재고는 건드리지 않고 PG 금액으로 SYSTEM 환불만 생성합니다.
  - 통화 불일치: 결제 · 주문 · 포인트 · 재고를 그대로 두고 ERROR 로그만 남깁니다. 승인 근거도, 실패 확정도, SYSTEM 환불 금액의 근거도 아니며 운영이 실제 PG 거래를 확인합니다.
  - 그 밖(이미 PAID · 상태 불일치 · 미결제 · 주문 불일치): 아무것도 바꾸지 않습니다.
- SYSTEM 환불은 결제당 최대 1건입니다. 재전송 · 재조회로 다시 들어와도 기존 행을 돌려주고 새로 만들지 않습니다. 실제 PortOne 취소는 지연 큐 Worker가 다시 조회한 뒤 실행합니다(통화 KRW · 원수납액이 환불 금액과 같을 때만).
- 결제 확정 API · 웹훅 · 만료 큐는 같은 Processor를 쓰고 PAYMENT 잠금 아래에서 끝난 상태를 다시 바꾸지 않으므로, 동시에 들어와도 결제 성공은 한 번만 반영됩니다. 웹훅이 먼저 반영했으면 이 API는 200(이미 PAID)입니다.
- 주문 PAID 커밋 뒤 별도 트랜잭션에서 결제한 상품을 장바구니에서 정리합니다(주문 도메인 리스너). 실패해도 결제 확정은 되돌리지 않습니다.
- 응답은 커밋 뒤 결제 행을 다시 읽어 만듭니다.

---

## 3. 결제 취소(회원)

### API: 결제 취소(회원)

### Method / Path

`POST /api/payments/{paymentId}/cancel`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |
| Idempotency-Key | O | 취소 버튼을 누를 때마다 새로 만드는 값(UUID 권장), 최대 64자. 재시도할 때만 같은 값을 보냄. 정규화하지 않고 그대로 비교하며(대소문자 구분), 모든 회원 환불에서 UNIQUE |

### Request

```json
{
  "reason": "단순 변심"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| reason | String | O | 취소 사유, 공백 불가 · 최대 500자. 주문의 취소 사유 상세와 환불 사유에 저장되고 PortOne 취소 사유로 전달 |

### Response `200 OK` (취소 완료) / `202 Accepted` (PortOne 결과 대기)

결제 전 취소(200)

```json
{
  "success": true,
  "code": "OK",
  "message": "결제 대기 주문을 취소했습니다.",
  "data": {
    "paymentId": 2001,
    "orderId": 1001,
    "paymentStatus": "FAILED",
    "failureReason": "CANCELED_BEFORE_PAY",
    "canceledAt": null,
    "refundStatus": null,
    "refundAmount": 0
  }
}
```

결제 후 취소 완료(200)

```json
{
  "success": true,
  "code": "OK",
  "message": "결제가 취소되었습니다.",
  "data": {
    "paymentId": 2001,
    "orderId": 1001,
    "paymentStatus": "CANCELED",
    "failureReason": null,
    "canceledAt": "2026-11-04T21:40:12+09:00",
    "refundStatus": "COMPLETED",
    "refundAmount": 7000
  }
}
```

결제 후 취소 결과 대기(202)

```json
{
  "success": true,
  "code": "OK",
  "message": "취소 결과를 확인하고 있습니다.",
  "data": {
    "paymentId": 2001,
    "orderId": 1001,
    "paymentStatus": "CANCEL_REQUESTED",
    "failureReason": null,
    "canceledAt": null,
    "refundStatus": "REQUESTED",
    "refundAmount": 7000
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| paymentId / orderId | Long | 결제 · 주문 ID |
| paymentStatus | String | 처리 후 결제 상태: FAILED(결제 전 취소) / CANCELED(취소 완료) / CANCEL_REQUESTED(결과 대기) |
| failureReason | String / null | 결제 전 취소면 CANCELED_BEFORE_PAY, 그 밖에는 null |
| canceledAt | String / null | 카드 결제는 PortOne이 준 취소 시각, 0원 결제는 서버가 내부 취소를 끝낸 시각. 결제 전 취소 · 결과 대기는 null |
| refundStatus | String / null | 이번 환불 작업의 상태(REQUESTED / COMPLETED). 결제 전 취소 · 0원 결제는 환불 작업이 없어 null |
| refundAmount | Long | 이번 환불 작업의 금액. REQUESTED여도 전액이 들어감. 결제 전 취소 · 0원 결제는 0 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | reason 누락 · 공백 · 500자 초과, Idempotency-Key 64자 초과 |
| 400 | COMMON-007 (MISSING_IDEMPOTENCY_KEY) | Idempotency-Key 헤더 누락 또는 빈 값 |
| 404 | PAYMENT-001 (PAYMENT_NOT_FOUND) | 결제가 없거나 다른 회원의 결제 |
| 409 | PAYMENT-007 (INVALID_PAYMENT_STATUS) | 이미 CANCELED인 카드 결제에 새 키로 요청, 결제 전 취소가 아닌 사유로 FAILED된 결제, 결제는 PAID인데 주문이 PAID가 아님, 0원 결제가 PAID · CANCELED가 아님 |
| 409 | PAYMENT-008 (ORDER_ALREADY_PREPARING) | 결제 완료 후 마트 처리 상태가 RECEIVED가 아님(배송 준비 시작 이후) |
| 409 | PAYMENT-009 (PAYMENT_CANCEL_REJECTED) | PortOne이 취소 실패(cancellation.status = FAILED)를 명시. 결제 · 주문 PAID 복귀, 환불 FAILED. 같은 키 재요청도 같은 응답 |
| 409 | COMMON-006 (DUPLICATE_REQUEST_IN_PROGRESS) | 다른 Idempotency-Key의 취소가 진행 중(결제 CANCEL_REQUESTED) |
| 409 | COMMON-011 (IDEMPOTENCY_KEY_PAYLOAD_MISMATCH) | 같은 키가 다른 결제 · 다른 사유 · 회원 환불이 아닌 환불에 쓰였음, 다른 결제에 같은 키가 동시에 들어와 UNIQUE 충돌 |
| 409 | COMMON-010 (LOCK_ACQUISITION_TIMEOUT) | 결제 · 주문 · 포인트 · 재고 락 대기 시간 초과 |
| 500 | COMMON-008 (EXTERNAL_SERVICE_ERROR) | PortOne이 취소 요청을 처리 전에 거절(400 · 401 · 403). 결제 · 주문 PAID 복귀, 환불 FAILED. 같은 키 재요청도 같은 응답 |

### 비즈니스 규칙

- 결제 전 · 후 취소가 같은 엔드포인트입니다. Facade는 트랜잭션을 걸지 않고 `TX1(접수) → PortOne 전액 취소 → TX2(반영)` 순으로 나눕니다. 결제 전 취소 · 0원 결제 취소 · 같은 키 재요청의 일부는 TX1에서 끝납니다.
- TX1 판정 순서(PAYMENT 행 비관적 락 아래):
  1. 결제 소유 회원 확인(`PAYMENT-001`). 멱등 판정보다 먼저 봐서 남의 결제가 이미 취소됐는지를 200/404 차이로 알 수 없게 합니다.
  2. 0원 결제(POINTS_ONLY): 이미 CANCELED면 현재 완료 결과를 200으로 반환(자산 변경 없음), PAID가 아니면 `PAYMENT-007`, PAID면 주문 취소 접수 → 결제 CANCELED(서버 시각) → 주문 REFUNDED → 사용 포인트 복원 · 적립 예정 취소 → 재고 복구까지 이 트랜잭션에서 끝내고 200. PortOne · 환불 행이 없어 Idempotency-Key별 결과는 저장하지 않습니다.
  3. 같은 Idempotency-Key의 환불이 있으면 재요청으로 봅니다. 다른 결제 · 다른 사유 · MEMBER 환불이 아니면 `COMMON-011`. COMPLETED면 200 취소 완료, FAILED면 원래 실패 응답(`PAYMENT-009` 409 / `COMMON-008` 500)을 PortOne 호출 없이 재현, REQUESTED면 PortOne 취소 ID가 없고 접수 후 3시간(PortOne 멱등 보장 시간) 안일 때만 같은 서버 키로 PortOne을 다시 부르고, 아니면 202.
  4. 결제 IN_PROGRESS → 결제 전 취소: 결제 FAILED(CANCELED_BEFORE_PAY) → 주문 CANCELED(MEMBER_CANCELED) → 사용 포인트 복원 · 적립 예정 취소 → 재고 복구, 200. PortOne 호출 · 환불 행 생성 없음, 키도 저장하지 않습니다. 만료 시각이 지났어도 아직 IN_PROGRESS면 이 경로로 처리합니다.
  5. 이미 결제 전 취소로 FAILED(CANCELED_BEFORE_PAY)면 같은 종료 결과를 200으로 반환합니다(다른 키여도 동일).
  6. 결제 PAID → 주문이 먼저 판정(PAID · RECEIVED만 허용, 아니면 `PAYMENT-008` / `PAYMENT-007`) → 주문 CANCEL_REQUESTED(MEMBER_CANCELED) · 결제 CANCEL_REQUESTED · 환불 REQUESTED(MEMBER, 결제 금액 전액, 클라이언트 키 저장) 커밋. 환불 행은 즉시 flush해 키 UNIQUE 충돌을 여기서 `COMMON-011`로 받습니다.
  7. 결제 CANCEL_REQUESTED(다른 키로 진행 중) → `COMMON-006`, 그 밖(CANCELED · 다른 사유의 FAILED) → `PAYMENT-007`
- TX1 커밋으로 잠금이 풀린 뒤 PortOne `POST /payments/{id}/cancel`을 호출합니다. 금액 없이 전액 취소이며, `currentCancellableAmount`에 결제 금액, `requester=CUSTOMER`, PortOne Idempotency-Key에는 클라이언트 키가 아니라 환불 행마다 고정된 서버 키(`recipick-refund-{refundId}`)를 보냅니다.
- TX2(PAYMENT 락 다시 획득) 반영:
  - 취소 성공(SUCCEEDED + 취소 시각): 결제 CANCELED · 환불 COMPLETED · 주문 REFUNDED → 사용 포인트 복원 · 적립 예정 취소 → 재고 복구, 200
  - 비동기 처리 중(REQUESTED) 또는 SUCCEEDED인데 취소 시각 없음: 취소 ID만 기록하고 상태 유지, 202
  - 명시적 실패(FAILED) · 처리 전 거절(400 · 401 · 403): 환불 FAILED(실패 종류 저장), 결제 · 주문 PAID 복귀(마트 RECEIVED 유지), 409 `PAYMENT-009` / 500 `COMMON-008`
  - 404 · 상태 불일치 409 · timeout · 5xx · 해석 불가: 성공도 실패도 단정하지 않고 CANCEL_REQUESTED / REQUESTED 유지, 202
  - TX2 시점에 환불이 이미 COMPLETED · FAILED(웹훅 · 동시 재요청이 먼저 반영)면 바꾸지 않고 그 결과를 돌려줍니다.
- 202이면 웹훅만 기다리지 않고 5분 뒤 환불 재확인을 Redis 지연 큐에 예약합니다. 예약 실패는 202 응답을 바꾸지 않으며 REQUESTED 환불을 Fallback이 다시 등록합니다.
- 실패 응답의 예외는 TX2 커밋 뒤에 던집니다. 그래서 PAID 복귀 · 환불 FAILED는 409/500 응답이어도 남습니다.
- 포인트는 결제 후 취소의 TX1에서 건드리지 않고 PG 환불이 성공한 TX2에서만 되돌립니다. 환불이 실패해 PAID로 돌아와도 포인트만 돌아간 채 남지 않게 하기 위함입니다. 배송완료로 적립이 확정된 뒤의 포인트 회수는 없습니다.
- 락 순서는 PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고이며, 주문 수락(마트)과 동시에 들어와도 주문 행 락으로 한쪽만 성공합니다.
- 결제 전 취소와 PortOne 결제 승인이 엇갈려 FAILED 결제에 PG 승인이 뒤늦게 확인되면, 결제 확정 · 웹훅 · 만료 경로가 SYSTEM 환불로 돈을 돌려줍니다(2절).

---

## 4. 주문별 결제 조회

### API: 주문별 결제 조회

### Method / Path

`GET /api/payments/orders/{orderId}`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

없음

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "결제 정보를 조회했습니다.",
  "data": {
    "paymentId": 2001,
    "orderId": 1001,
    "portonePaymentId": "pay_3f9c1e2a-5b7d-4c8e-9a1f-2b3c4d5e6f70",
    "paymentMethod": "CARD",
    "amount": 7000,
    "paymentStatus": "IN_PROGRESS",
    "failureReason": null,
    "paidAt": null,
    "canceledAt": null,
    "expiresAt": "2026-11-04T21:35:00+09:00",
    "completedRefundAmount": 0,
    "latestRefundStatus": null
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| paymentId / orderId | Long | 결제 · 주문 ID |
| portonePaymentId | String / null | PortOne 결제 ID. 결제창 재진입에 사용. 0원 결제는 null |
| paymentMethod | String | CARD / POINTS_ONLY |
| amount | Long | 결제 금액 |
| paymentStatus | String | 결제 상태 |
| failureReason | String / null | 실패 사유. 실패가 아니면 null |
| paidAt | String / null | 결제 완료 시각 |
| canceledAt | String / null | 취소 완료 시각 |
| expiresAt | String / null | 결제 만료 시각(생성 + 5분). 0원 결제는 null |
| completedRefundAmount | Long | 이 결제의 COMPLETED 환불 금액 합계. 환불이 없으면 0 |
| latestRefundStatus | String / null | 가장 최근(id가 가장 큰) 환불의 상태. 환불이 없으면 null |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 404 | PAYMENT-001 (PAYMENT_NOT_FOUND) | 주문의 결제가 없거나 다른 회원의 주문 |

### 비즈니스 규칙

- 상태를 바꾸지 않아 Facade 트랜잭션 · 잠금이 없고 각 Service의 읽기 트랜잭션만 씁니다.
- 다른 회원의 주문은 403이 아니라 404로 응답합니다. 환불 이력은 본인 주문으로 확인된 뒤에만 읽습니다.
- 이어서 결제: `paymentStatus`가 IN_PROGRESS이고 현재 시각이 `expiresAt` 전이면 `portonePaymentId` · `amount`와 주문의 `orderName`으로 PortOne 결제창을 다시 엽니다. 그 밖의 상태이거나 만료됐으면 결제창을 열지 않습니다.
- `completedRefundAmount`는 COMPLETED 환불의 합계라 취소 응답의 `refundAmount`(이번 환불 작업 금액, REQUESTED 포함)와 뜻이 다릅니다. 금액 불일치 SYSTEM 환불은 PG 청구 금액으로 적히므로 결제 금액과 다를 수 있습니다.

---

## 5. 결제 목록 조회

### API: 결제 목록 조회

### Method / Path

`GET /api/payments`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request (Query)

`GET /api/payments?page=0&size=20`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| page | Integer | X | 0부터, 기본 0 |
| size | Integer | X | 기본 20, 최대 100 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "결제 내역을 조회했습니다.",
  "data": {
    "content": [
      {
        "paymentId": 2002,
        "orderId": 1002,
        "paymentMethod": "CARD",
        "amount": 12000,
        "paymentStatus": "CANCELED",
        "failureReason": null,
        "paidAt": "2026-11-04T20:10:00+09:00",
        "canceledAt": "2026-11-04T20:20:00+09:00",
        "createdAt": "2026-11-04T20:09:30+09:00",
        "completedRefundAmount": 12000,
        "latestRefundStatus": "COMPLETED"
      },
      {
        "paymentId": 2001,
        "orderId": 1001,
        "paymentMethod": "CARD",
        "amount": 7000,
        "paymentStatus": "PAID",
        "failureReason": null,
        "paidAt": "2026-11-04T19:31:10+09:00",
        "canceledAt": null,
        "createdAt": "2026-11-04T19:30:00+09:00",
        "completedRefundAmount": 0,
        "latestRefundStatus": null
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].paymentId / orderId | Long | 결제 · 주문 ID |
| content[].paymentMethod | String | CARD / POINTS_ONLY |
| content[].amount | Long | 결제 금액 |
| content[].paymentStatus | String | 결제 상태 |
| content[].failureReason | String / null | 실패 사유 |
| content[].paidAt / canceledAt | String / null | 결제 완료 · 취소 완료 시각 |
| content[].createdAt | String | 결제 생성 시각(주문 생성 시각) |
| content[].completedRefundAmount | Long | COMPLETED 환불 금액 합계 |
| content[].latestRefundStatus | String / null | 가장 최근 환불 상태 |
| page / size / totalElements / totalPages | Integer / Integer / Long / Integer | 페이지 정보 |

### 예외처리

없음(공통 인증 오류만)

### 비즈니스 규칙

- PAYMENT에는 회원 정보가 없어 주문 도메인이 준 회원의 주문 ID 목록으로 좁힙니다. 주문이 없으면 빈 페이지입니다.
- `createdAt` 내림차순, 같으면 `id` 내림차순으로 고정하며 `sort` 파라미터는 무시합니다.
- 환불 요약은 이번 페이지 결제만 한 번에 읽어 계산합니다.
- `portonePaymentId` · `expiresAt`은 결제창 재진입용이라 목록에 넣지 않고 4절(주문별 결제 조회)에만 둡니다.

---

## 6. PortOne 웹훅 수신

### API: PortOne 웹훅 수신

### Method / Path

`POST /api/webhooks/portone`

### 인증

- JWT 인증 없음(WEBHOOK). PortOne V2 Standard Webhooks 서명으로 인증합니다.

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| webhook-id | O | PortOne 웹훅 ID. 중복 수신 판정 키 |
| webhook-timestamp | O | 발송 시각(epoch 초) |
| webhook-signature | O | `v1,{Base64 서명}`. 공백으로 구분된 여러 개 중 하나만 맞으면 통과 |

### Request

```json
{
  "type": "Transaction.Paid",
  "timestamp": "2026-11-04T12:31:10.000Z",
  "data": {
    "storeId": "store-00000000-0000-0000-0000-000000000000",
    "paymentId": "pay_3f9c1e2a-5b7d-4c8e-9a1f-2b3c4d5e6f70",
    "transactionId": "txn_0000000000000000"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| type | String | O | 웹훅 종류. 처리 대상: `Transaction.Paid`, `Transaction.Failed`, `Transaction.CancelPending`, `Transaction.Cancelled`, `Transaction.PartialCancelled` |
| timestamp | String | X | PortOne 이벤트 시각 |
| data.storeId | String | O | PortOne 상점 ID. 서버 설정과 같아야 처리 |
| data.paymentId | String | O | PortOne 결제 ID |
| data.transactionId | String | X | PG 거래 ID |
| data.cancellationId | String | X | 취소 웹훅의 취소 ID |

- 본문은 서명 검증을 위해 원문 그대로(String) 받습니다. 모르는 필드는 무시합니다.

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "웹훅을 접수했습니다.",
  "data": null
}
```

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | 본문이 없음, 서명은 맞는데 본문을 JSON으로 읽을 수 없음 |
| 401 | AUTH-010 (INVALID_WEBHOOK_SIGNATURE) | 서명 헤더 셋 중 하나라도 없거나 빈 값, timestamp가 숫자가 아님, timestamp가 현재 시각 ±5분 밖, 일치하는 v1 서명 없음 |
| 500 | PAYMENT-011 (WEBHOOK_NOT_CONFIGURED) | 서버에 웹훅 시크릿이 없음, 처리 대상 type인데 서버 storeId 설정이 없음 |
| 500 | COMMON-500 (INTERNAL_SERVER_ERROR) | 저장 중 webhook_id 중복이 아닌 무결성 오류 등 예상하지 못한 오류 |

### 비즈니스 규칙

- 요청 스레드는 `서명 검증 → 본문 해석 → inbox(WEBHOOK_EVENT) 저장 → 지연 큐 등록 → 200`까지만 합니다. PortOne 재조회 · 결제 상태 변경 · 잠금은 하지 않습니다(PortOne은 30초 안에 응답이 없으면 재전송).
- 서명 검증: 서명 대상은 `{webhook-id}.{timestamp(파싱한 정수)}.{본문 원문}`, HMAC-SHA256, 시크릿은 `whsec_` 접두어를 뗀 Base64 값입니다. 비교는 상수 시간으로 합니다. 검증 실패 웹훅은 위조 · 재생일 수 있어 DB에 아무것도 남기지 않습니다.
- 저장 규칙(모든 경우 200):
  - 처리 대상이 아닌 type(BillingKey.* 등) · 다른 storeId · paymentId 없음: IGNORED 상태로 저장만 하고 큐에 넣지 않습니다. 저장해 두어야 같은 webhook_id 재전송이 UNIQUE에 걸려 조회 없이 끝납니다.
  - 처리 대상: RECEIVED로 저장하고 커밋 뒤 Redis 지연 큐에 `WEBHOOK_PROCESS` 작업을 즉시 실행으로 등록합니다.
  - 같은 webhook_id 재전송: `uk_webhook_event_webhook_id` UNIQUE 충돌(제약 이름이 다르면 webhook_id 존재 여부로 한 번 더 확인)을 "이미 받은 웹훅"으로 보고 저장 없이 200을 반환합니다.
- 웹훅 경로(`/api/webhooks/**`)는 요청 로그 필터(`RequestLoggingFilter`)와 요청 수 제한 필터(`RateLimitFilter`)에서 모두 제외됩니다. 서명 검증에 쓰는 원문 본문은 바꾸지 않으며(로그 필터는 본문을 읽거나 바꾸지 않습니다), 웹훅 처리 추적은 도메인 로그와 `webhook_event` ID로 합니다.
- 큐 등록이 Redis 장애로 실패해도 200을 반환합니다. RECEIVED 행이 DB에 남아 1시간 주기 Fallback Sweep이 다시 등록합니다(500을 주면 PortOne 재전송이 UNIQUE에 걸려 아무것도 못 하기 때문).
- 비동기 처리(Worker가 1초 간격으로 큐에서 최대 20건씩 꺼냄):
  1. TX-A: 웹훅 행을 잠그고 PROCESSING으로 바꿔 커밋(처리권 번호 = 시도 횟수). 이미 끝났거나 다른 Worker가 처리 중이면 건너뜁니다.
  2. `mpay_`로 시작하는 결제 ID는 멤버십 웹훅 정합화로 보냅니다.
  3. 상품 결제: 레시픽 결제가 아니면 IGNORED, `Transaction.PartialCancelled`는 자동 반영 규칙이 없어 PortOne 조회 없이 MANUAL_REVIEW.
  4. 락 없이 PortOne 결제를 재조회합니다. 웹훅 본문의 상태는 믿지 않고 어느 결제를 볼지 정하는 데만 씁니다.
  5. TX-B: `Paid` · `Failed`는 결제 확정 API와 같은 Processor로 반영하고(2절 판정 · 반영과 동일), 취소 계열은 기존 환불(취소 ID → 취소 ID 없는 REQUESTED 환불 1건)에 묶어 반영하며, 대응하는 환불이 없으면 PortOne 콘솔 등 외부 전액 취소일 때만 EXTERNAL 환불로 반영합니다.
  6. TX-C: 처리권이 아직 내 것일 때만 결과(PROCESSED · IGNORED · RETRY_PENDING · MANUAL_REVIEW)를 기록합니다.
- 재시도: PortOne 조회 실패 · PG 미확정 · 일시적 DB 장애는 RETRY_PENDING으로 두고 1분 → 2분 → 4분 → 8분 간격으로 다시 잡습니다. 5회까지 끝내지 못하면 MANUAL_REVIEW입니다. 통화 불일치 · customData 주문 불일치 · 예상하지 못한 내부 오류는 바로 MANUAL_REVIEW입니다.
- PROCESSING인 채 10분이 지나면 Worker가 멈춘 것으로 보고 Fallback Sweep이 다시 큐에 넣습니다.

---

## 7. 빌링키 발급 요청 생성

### API: 빌링키 발급 요청 생성

### Method / Path

`POST /api/billing-keys/issue-requests`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

없음(본문 없음)

### Response `200 OK`

응답 헤더 `Cache-Control: no-store`

```json
{
  "success": true,
  "code": "OK",
  "message": "카드 등록을 시작합니다.",
  "data": {
    "issueId": "rcp-bk-8a1b2c3d-0000-4000-8000-000000000000",
    "issueName": "레시픽 멤버십 자동결제 카드 등록",
    "storeId": "store-00000000-0000-0000-0000-000000000000",
    "channelKey": "channel-key-00000000-0000-0000-0000-000000000000",
    "customerId": "member-7",
    "displayAmount": 3900,
    "currency": "KRW",
    "expiresAt": "2026-11-04T22:00:00+09:00"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| issueId | String | 일회성 카드 등록 토큰(`rcp-bk-` + UUID). SDK `requestIssueBillingKey`의 `issueId`에 그대로 넣음 |
| issueName | String | 발급 창 이름(고정 문구) |
| storeId / channelKey | String | PortOne 상점 ID · 카드 등록 전용 정기결제 채널 키(`portone.billing-channel-key`). 주문 결제의 `GET /api/config/portone`이 주는 일반결제 채널 키와 다르다 |
| customerId | String | PortOne 고객 ID(`member-` + 회원 ID). 토스페이먼츠 customerKey 규칙(2자 이상)에 맞춘 값으로, SDK `customer.customerId`에 그대로 넣음 |
| displayAmount | Long | 발급 창 표시 금액(3,900원). 실제 청구 금액이 아님 |
| currency | String | KRW |
| expiresAt | String | 토큰 만료 시각(생성 + 30분) |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 404 | MEMBER-001 (MEMBER_NOT_FOUND) | 회원이 없거나 탈퇴함 |
| 500 | COMMON-008 (EXTERNAL_SERVICE_ERROR) | 서버에 PortOne storeId · channelKey 설정이 없음 |
| 503 | BILLING-008 (BILLING_KEY_REGISTRATION_DISABLED) | 카드 등록 기능 스위치(`recipick.billing-key.registration-enabled`)가 꺼져 있음 |

### 비즈니스 규칙

- 검사 순서: 기능 스위치(503) → PortOne 상점 · 채널 설정(500) → 회원 존재 · 탈퇴(404) → 발급 요청 행 저장(REQUESTED).
- 토큰은 무작위 UUID라 추측할 수 없고, 회원 잠금은 걸지 않습니다. 요청할 때마다 새 행이 생깁니다.
- `issueId`는 이 회원 세션에만 가는 일회성 토큰이라 `Cache-Control: no-store`로 내려가고 DTO `toString`에서 가려집니다.
- 기능 스위치는 운영 기본 false입니다. 스위치를 켜도 실채널(LIVE) 빌링키는 8절 검증에서 코드로 거절됩니다.

---

## 8. 빌링키 등록

### API: 빌링키 등록

### Method / Path

`POST /api/billing-keys`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

```json
{
  "issueId": "rcp-bk-8a1b2c3d-0000-4000-8000-000000000000",
  "billingKey": "billing-key-0000000000000000"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| issueId | String | O | 7절에서 받은 토큰, 공백 불가 · 최대 64자 |
| billingKey | String | O | SDK 발급 결과 빌링키, 공백 불가 · 최대 255자 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "카드가 등록되었습니다.",
  "data": {
    "billingKeyId": 31,
    "status": "ACTIVE",
    "outcome": "REGISTERED"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| billingKeyId | Long | 등록(또는 재활성화 · 기존)된 빌링키 ID |
| status | String | 항상 ACTIVE |
| outcome | String | REGISTERED / REPLACED / ALREADY_ACTIVE / REACTIVATED (공통 상태 값 표) |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | issueId · billingKey 누락 · 공백 · 길이 초과 |
| 400 | BILLING-001 (INVALID_BILLING_KEY) | PortOne에 빌링키가 없음(404), ISSUED가 아님, 키 · 상점 · 채널 불일치, 채널 type이 TEST가 아님(실채널), PortOne이 issueId를 돌려주지 않음 · 다름, customer.id가 로그인 회원과 다름, 카드 수단이 아님 |
| 404 | MEMBER-001 (MEMBER_NOT_FOUND) | 회원 잠금 시점에 회원이 없거나 탈퇴함 |
| 409 | BILLING-002 (BILLING_KEY_ALREADY_USED) | 다른 회원이 등록한 키, 두 회원이 같은 키를 동시에 등록해 UNIQUE 충돌 |
| 409 | BILLING-006 (BILLING_KEY_UNUSABLE) | 내 키지만 REVOKED · DELETE_REQUESTED · DELETED |
| 409 | BILLING-007 (BILLING_KEY_ISSUE_REQUEST_INVALID) | 발급 요청이 없음 · 다른 회원 것 · 30분 만료, 이미 다른 키에 사용됨, 교체된(REPLACED) 키를 유효기간이 지난 증빙으로 되살리려 함 |
| 409 | COMMON-010 (LOCK_ACQUISITION_TIMEOUT) | 회원 · 빌링키 락 대기 시간 초과 |
| 500 | COMMON-008 (EXTERNAL_SERVICE_ERROR) | PortOne 상점 · 채널 설정 없음, PortOne 조회 401 · 403 · 그 밖의 오류 응답 · 빈 응답 · 상태 해석 불가 · 연결 실패 |
| 500 | COMMON-009 (EXTERNAL_SERVICE_TIMEOUT) | PortOne 조회 timeout |
| 503 | BILLING-008 (BILLING_KEY_REGISTRATION_DISABLED) | 카드 등록 기능 스위치가 꺼져 있음 |

### 비즈니스 규칙

- 처리 순서(Facade는 트랜잭션 없음, 바깥 트랜잭션 안에서 호출 불가):
  1. 기능 스위치(503). 입력 검증(400)은 컨트롤러에서 이보다 먼저 합니다.
  2. 발급 요청 1차 확인(트랜잭션 밖): 존재 · 본인 소유 · 제출 가능(REQUESTED면 유효기간 안, USED면 이 키에 묶였고 그 키가 REPLACED면 유효기간 안). 아니면 `BILLING-007`.
  3. PortOne 상점 · 채널 설정 확인(500).
  4. PortOne `GET /billing-keys/{billingKey}` 조회(회원 잠금 없이).
  5. 발급 검증 순서: ISSUED → 키 일치 → 상점 일치 → 우리 채널 키의 채널 객체 존재 → 그 채널 type이 `TEST` → PortOne issueId 있음 → issueId가 서버 토큰과 같음 → customer.id가 있으면 회원 ID와 같음 → 카드 수단. 하나라도 어긋나면 `BILLING-001`. 실채널 · issueId 미반환은 설정 · PG 동작 이상이라 ERROR, 나머지는 WARN 로그.
  6. 등록 트랜잭션: 첫 DB 작업으로 회원을 잠그고(탈퇴면 `MEMBER-001`) 발급 요청을 다시 검사 → 같은 키 행을 PK로 잠금 → 현재 ACTIVE 행 잠금 → 분기.
  7. 커밋 뒤 그 회원의 카드 대기(PAYMENT_METHOD_REQUIRED 등) 멤버십 갱신 회차 재개를 시도합니다. 재개 실패는 로그만 남기고 등록 성공 응답은 그대로입니다.
- 분기표:
  - 같은 키 행 없음: 기존 ACTIVE가 있으면 REPLACED로 바꾸고 새 키를 ACTIVE로 저장(`REPLACED`), 없으면 `REGISTERED`. 발급 요청은 USED로 이 키에 묶입니다.
  - 같은 키가 다른 회원 것: `BILLING-002`
  - 내 키가 ACTIVE: 변경 없음(`ALREADY_ACTIVE`, 멱등)
  - 내 키가 REPLACED: 현재 ACTIVE를 REPLACED로 바꾸고 이 키를 다시 ACTIVE로(`REACTIVATED`). 발급 뒤 30분 안의 증빙으로만 허용
  - 내 키가 REVOKED · DELETE_REQUESTED · DELETED: `BILLING-006`
- 같은 회원의 등록 · 교체는 회원 잠금으로 직렬화됩니다. 같은 토큰으로 키 두 개를 동시에 보내면 늦은 쪽이 트랜잭션 안 재검사에서 `BILLING-007`을 받습니다.
- 두 회원이 같은 키를 동시에 등록하면 즉시 flush한 `uk_billing_key_portone_billing_key` UNIQUE가 한 명만 통과시키고 나머지는 `BILLING-002`입니다.
- 같은 키 재등록도 200입니다. 결과 구분은 `outcome`으로 합니다.
- 빌링키 · 토큰 원문은 응답 · 로그 · `toString` · 예외 cause에 넣지 않습니다. 카드번호 · CVC는 받지도 저장하지도 않습니다.

---

## 9. 내 결제수단 조회

### API: 내 결제수단 조회

### Method / Path

`GET /api/billing-keys/me`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

없음

### Response `200 OK`

등록된 카드가 있을 때

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "registered": true,
    "billingKeyId": 31,
    "status": "ACTIVE",
    "issuedAt": "2026-11-04T21:45:00+09:00"
  }
}
```

등록된 카드가 없을 때

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "registered": false,
    "billingKeyId": null,
    "status": null,
    "issuedAt": null
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| registered | Boolean | ACTIVE 빌링키가 있으면 true |
| billingKeyId | Long / null | ACTIVE 빌링키 ID |
| status | String / null | ACTIVE. 없으면 null |
| issuedAt | String / null | PortOne이 알려 준 빌링키 발급 시각 |

### 예외처리

없음(공통 인증 오류만)

### 비즈니스 규칙

- ACTIVE 빌링키만 봅니다. REPLACED · REVOKED · DELETE_REQUESTED · DELETED 키만 있는 회원은 `registered: false`입니다.
- 카드사 · 카드 끝 4자리는 저장하지 않으므로 등록 여부 · 등록일만 내려줍니다. 빌링키 원문은 응답에 없습니다.
- 카드 등록 기능 스위치와 무관하게 동작합니다.

---

## 10. 포인트 잔액 조회

### API: 포인트 잔액 조회

### Method / Path

`GET /api/points`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

없음

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "포인트를 조회했습니다.",
  "data": {
    "memberId": 7,
    "availableBalance": 1250,
    "nearestExpiringAmount": 300,
    "nearestExpiresOn": "2027-03-02"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| memberId | Long | 회원 ID(토큰의 회원) |
| availableBalance | Long | 오늘 쓸 수 있는 포인트 합계 |
| nearestExpiringAmount | Long | 가장 가까운 만료일에 만료되는 사용 가능 포인트 합계. 없으면 0 |
| nearestExpiresOn | String / null | 가장 가까운 만료일(`yyyy-MM-dd`). 없으면 null |

### 예외처리

없음(공통 인증 오류만)

### 비즈니스 규칙

- 사용 가능 포인트는 `POINT_EARNING` 중 COMPLETED이고 남은 양이 있으며 만료일이 오늘 이후(오늘 포함)인 적립 건의 남은 양 합계입니다. 만료 배치가 아직 처리하지 않은 만료 잔량은 날짜 조건으로 계산에서 뺍니다.
- 적립 예정(PENDING) 포인트는 잔액에 포함하지 않습니다.
- 가장 가까운 만료는 같은 조건의 잔량을 만료일별로 합친 뒤 가장 이른 날짜 한 줄입니다. 잔액과 같은 읽기 트랜잭션에서 읽어 같은 시점 값으로 맞춥니다.
- 포인트 이력이 없는 회원도 404가 아니라 0 · null로 답합니다.
- 조회는 상태를 바꾸지 않습니다. 잠금 · 만료 처리 · 이력 INSERT · 잔액 수정을 하지 않으며, 실제 소멸은 만료 배치와 포인트 사용 직전의 지연 만료가 맡습니다.

---

## 11. 포인트 적립 내역 조회

### API: 포인트 적립 내역 조회

### Method / Path

`GET /api/points/earnings`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request (Query)

`GET /api/points/earnings?page=0&size=20`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| page | Integer | X | 0부터, 기본 0 |
| size | Integer | X | 기본 20, 최대 100 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "적립 내역을 조회했습니다.",
  "data": {
    "content": [
      {
        "earningId": 52,
        "orderId": 1002,
        "status": "PENDING",
        "amount": 60,
        "availableRemainingAmount": 0,
        "confirmedAt": null,
        "expiresOn": null,
        "usable": false,
        "createdAt": "2026-11-04T20:10:00+09:00"
      },
      {
        "earningId": 41,
        "orderId": 950,
        "status": "COMPLETED",
        "amount": 300,
        "availableRemainingAmount": 300,
        "confirmedAt": "2026-03-02T15:00:00+09:00",
        "expiresOn": "2027-03-02",
        "usable": true,
        "createdAt": "2026-02-28T11:20:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].earningId | Long | 적립 건 ID |
| content[].orderId | Long | 적립을 만든 주문 ID |
| content[].status | String | 적립 상태(PENDING / COMPLETED / CANCELED / EXPIRED / FORFEITED) |
| content[].amount | Long | 최초 적립량 |
| content[].availableRemainingAmount | Long | 오늘 쓸 수 있는 남은 양. 사용 가능하지 않으면 0 |
| content[].confirmedAt | String / null | 적립 확정 시각. 확정 전이면 null |
| content[].expiresOn | String / null | 만료일(확정일 + 1년). 확정 전이면 null |
| content[].usable | Boolean | COMPLETED이고 만료일이 오늘 이후(오늘 포함)이며 남은 양이 있으면 true |
| content[].createdAt | String | 적립 건 생성 시각 |
| page / size / totalElements / totalPages | Integer / Integer / Long / Integer | 페이지 정보 |

### 예외처리

없음(공통 인증 오류만)

### 비즈니스 규칙

- 상태와 관계없이 모든 적립 건을 보여줍니다. 적립 예정부터 확정 · 취소 · 만료까지가 한 적립 건의 기록입니다.
- DB의 남은 양을 그대로 보여주면 만료 배치 전의 만료 잔량이 쓸 수 있는 것처럼 보이므로, 오늘 날짜로 쓸 수 있는 잔량만 `availableRemainingAmount`로 내보냅니다. 조회 때문에 DB 값을 고치지 않습니다.
- `createdAt` 내림차순, 같으면 `id` 내림차순으로 고정하며 `sort`는 무시합니다.
- 적립 이력이 없는 회원은 빈 페이지입니다.

---

## 12. 포인트 이력 조회

### API: 포인트 이력 조회

### Method / Path

`GET /api/points/history`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request (Query)

`GET /api/points/history?orderId=1002&page=0&size=20`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Long | X | 1 이상. 지정하면 그 주문의 이력만 조회 |
| page | Integer | X | 0부터, 기본 0 |
| size | Integer | X | 기본 20, 최대 100 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "포인트 이력을 조회했습니다.",
  "data": {
    "content": [
      {
        "historyId": 205,
        "earningId": 41,
        "orderId": 1002,
        "type": "USE_CONFIRMED",
        "amount": 500,
        "createdAt": "2026-11-04T20:10:00+09:00"
      },
      {
        "historyId": 204,
        "earningId": 52,
        "orderId": 1002,
        "type": "EARN_PENDING",
        "amount": 60,
        "createdAt": "2026-11-04T20:09:30+09:00"
      },
      {
        "historyId": 203,
        "earningId": 41,
        "orderId": 1002,
        "type": "USE_RESERVED",
        "amount": 500,
        "createdAt": "2026-11-04T20:09:30+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].historyId | Long | 이력 ID |
| content[].earningId | Long | 포인트가 움직인 적립 건 ID |
| content[].orderId | Long / null | 관련 주문 ID. 만료 배치 · 지연 만료 · 탈퇴 소멸은 null |
| content[].type | String | 이력 종류(공통 상태 값 표) |
| content[].amount | Long | 움직인 포인트. 항상 양수이며 방향은 type으로 구분 |
| content[].createdAt | String | 이력 생성 시각 |
| page / size / totalElements / totalPages | Integer / Integer / Long / Integer | 페이지 정보 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | orderId가 0 이하 |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | orderId가 숫자가 아님 |

### 비즈니스 규칙

- 회원 조건이 항상 함께 붙어, 다른 회원의 주문 ID나 없는 주문 ID를 지정하면 오류 없이 빈 페이지입니다.
- 적립 건 ID와 주문 ID를 함께 내보내 어느 적립 건의 포인트가 어느 주문에서 움직였는지 추적할 수 있습니다. 적립 건은 이력 조회 쿼리에서 함께 읽어 이력마다 추가 조회가 생기지 않습니다.
- 만료일이 지난 포인트가 주문 종료로 복원되면 RESTORED 뒤 바로 EXPIRED 이력이 그 주문 ID로 남습니다.
- `createdAt` 내림차순, 같으면 `id` 내림차순으로 고정하며 `sort`는 무시합니다.

---

## 13. 관리자 결제 목록 조회

### API: 관리자 결제 목록 조회

### Method / Path

`GET /api/admin/payments`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request (Query)

`GET /api/admin/payments?memberId=7&from=2026-10-01&to=2026-10-31&page=0&size=20&sort=createdAt,desc`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| status | String | X | 결제 상태: IN_PROGRESS / PAID / FAILED / CANCEL_REQUESTED / CANCELED. 생략하면 전체 |
| memberId | Long | X | 주문 회원 ID, 1 이상 |
| from | String | X | 조회 시작일 `yyyy-MM-dd`. 그날 00:00부터 포함 |
| to | String | X | 조회 종료일 `yyyy-MM-dd`. 그날 하루 전체 포함(다음 날 00:00 미만) |
| page | Integer | X | 0부터, 기본 0 |
| size | Integer | X | 기본 20, 1~100(범위 밖이면 잘라내지 않고 COMMON-001) |
| sort | String | X | `createdAt,desc`(기본) 또는 `createdAt,asc`. 방향은 대소문자 구분 없음 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "결제 내역을 조회했습니다.",
  "data": {
    "content": [
      {
        "paymentId": 2001,
        "orderId": 1001,
        "memberId": 7,
        "paymentMethod": "CARD",
        "amount": 7000,
        "paymentStatus": "CANCELED",
        "failureReason": null,
        "paidAt": "2026-10-14T21:31:10+09:00",
        "canceledAt": "2026-10-14T21:50:02+09:00",
        "refundedAmount": 7000,
        "latestRefundStatus": "COMPLETED",
        "createdAt": "2026-10-14T21:30:00+09:00"
      },
      {
        "paymentId": 2000,
        "orderId": 1000,
        "memberId": 7,
        "paymentMethod": "CARD",
        "amount": 12000,
        "paymentStatus": "FAILED",
        "failureReason": "EXPIRED",
        "paidAt": null,
        "canceledAt": null,
        "refundedAmount": 0,
        "latestRefundStatus": null,
        "createdAt": "2026-10-13T18:02:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].paymentId | Long | 결제 ID |
| content[].orderId | Long | 주문 ID |
| content[].memberId | Long | 주문 회원 ID(주문 도메인에서 조회) |
| content[].paymentMethod | String | CARD / POINTS_ONLY(외부 결제금액 0원, PG 미경유) |
| content[].amount | Long | 결제 금액 |
| content[].paymentStatus | String | IN_PROGRESS / PAID / FAILED / CANCEL_REQUESTED / CANCELED |
| content[].failureReason | String / null | 결제 실패 사유: EXPIRED / CANCELED_BEFORE_PAY / AMOUNT_MISMATCH / PG_FAILED. 실패가 아니면 null |
| content[].paidAt | String / null | 결제 완료 시각 |
| content[].canceledAt | String / null | 결제 취소 시각 |
| content[].refundedAmount | Long | COMPLETED 환불 금액의 합계. 환불이 없으면 0 |
| content[].latestRefundStatus | String / null | 가장 최근(ID가 가장 큰) 환불 시도의 상태: REQUESTED / COMPLETED / FAILED. 환불이 없으면 null |
| content[].createdAt | String | 결제 생성 시각 |
| page / size / totalElements / totalPages | Integer / Integer / Long / Integer | 페이지 정보 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | 없는 status 값, memberId 0 이하, from · to 형식 오류, 없는 날짜(2026-02-30), from이 to보다 늦음, page 음수, size 1~100 밖, sort가 `createdAt,asc` · `createdAt,desc`가 아님, 숫자 파라미터에 문자 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 감사 기록 시점에 관리자 계정이 비활성화 상태 |
| 403 | AUTH-004 (ACCESS_DENIED) | 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름 |
| 500 | COMMON-500 (INTERNAL_SERVER_ERROR) | 결제에 대응하는 주문의 회원 정보를 찾을 수 없음(데이터 정합성 오류, null로 응답하지 않음) |

### 비즈니스 규칙

- `/api/admin/**`는 보안 설정상 MART_ADMIN에게도 열려 있어, 컨트롤러 클래스에 `@PreAuthorize("hasRole('SUPER_ADMIN')")`를 걸어 막습니다. MART_ADMIN은 403 AUTH-006입니다.
- status · from · to를 String으로 받는 이유는 enum · 날짜 변환 실패를 바인딩 오류(COMMON-002)가 아니라 Bean Validation(COMMON-001, `errors` 포함)으로 모으기 위해서입니다.
- 정렬은 `createdAt`만 허용하고, 같은 시각의 순서가 페이지마다 흔들리지 않도록 `id`를 같은 방향의 보조 정렬로 붙입니다.
- 기간은 결제 생성 시각(`createdAt`) 기준이며 한국 시간 `from 00:00 이상, to 다음 날 00:00 미만`입니다.
- PAYMENT에는 회원 정보가 없어, memberId 조건은 주문 도메인이 주는 그 회원의 주문 ID 목록으로 바꿔 겁니다. 주문이 없는 회원(없는 회원 포함)은 결제를 조회하지 않고 빈 페이지를 반환합니다.
- 환불 요약과 회원 ID는 페이지 안의 결제만 모아 한 번씩 조회합니다(결제마다 추가 쿼리 없음).
- 조회와 감사 기록을 한 쓰기 트랜잭션에서 처리합니다. 결과가 0건이어도 `PAYMENT_LIST_VIEW` 감사를 남기며, 검색 조건은 감사 로그에 복사하지 않습니다. 감사 기록이 실패하면 롤백되고 조회 결과도 반환하지 않습니다.
- 회원 상세 정보(이름 · 연락처 등), PortOne 결제 ID, 환불 시도 전체 이력은 목록에 넣지 않습니다. 상세에서 조회합니다.

---

## 14. 관리자 결제 상세 조회

### API: 관리자 결제 상세 조회

### Method / Path

`GET /api/admin/payments/{paymentId}`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| paymentId | Path | Long | O | 결제 ID |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "결제 상세를 조회했습니다.",
  "data": {
    "paymentId": 2001,
    "orderId": 1001,
    "memberId": 7,
    "paymentMethod": "CARD",
    "amount": 7000,
    "paymentStatus": "CANCELED",
    "failureReason": null,
    "portonePaymentId": "pay_3f9c1e2a-5b7d-4c8e-9a1f-2b3c4d5e6f70",
    "portoneTransactionId": "tx_0193a1b2c3d4e5f6",
    "expiresAt": "2026-10-14T21:35:00+09:00",
    "paidAt": "2026-10-14T21:31:10+09:00",
    "canceledAt": "2026-10-14T21:50:02+09:00",
    "refunds": [
      {
        "refundId": 302,
        "amount": 7000,
        "status": "COMPLETED",
        "requestedBy": "ADMIN",
        "reason": "재고 부족",
        "portoneCancellationId": "cancel_7a8b9c0d",
        "failureReason": null,
        "requestedAt": "2026-10-14T21:49:30+09:00",
        "completedAt": "2026-10-14T21:50:02+09:00"
      },
      {
        "refundId": 301,
        "amount": 7000,
        "status": "FAILED",
        "requestedBy": "ADMIN",
        "reason": "재고 부족",
        "portoneCancellationId": "cancel_1a2b3c4d",
        "failureReason": "PG_FAILED",
        "requestedAt": "2026-10-14T21:40:00+09:00",
        "completedAt": null
      }
    ]
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| paymentId / orderId / memberId | Long | 결제 · 주문 · 주문 회원 ID |
| paymentMethod | String | CARD / POINTS_ONLY |
| amount | Long | 결제 금액 |
| paymentStatus | String | IN_PROGRESS / PAID / FAILED / CANCEL_REQUESTED / CANCELED |
| failureReason | String / null | EXPIRED / CANCELED_BEFORE_PAY / AMOUNT_MISMATCH / PG_FAILED |
| portonePaymentId | String / null | PortOne 결제 ID(`pay_` + UUID). POINTS_ONLY는 null |
| portoneTransactionId | String / null | PortOne 거래 ID |
| expiresAt | String / null | 결제 만료 시각 |
| paidAt / canceledAt | String / null | 결제 완료 · 취소 시각 |
| refunds | Array | 이 결제의 환불 시도 전체(실패 후 재시도 포함), 최신 시도(ID 내림차순)부터. 없으면 `[]` |
| refunds[].refundId | Long | 환불 ID |
| refunds[].amount | Long | 환불 금액 |
| refunds[].status | String | REQUESTED / COMPLETED / FAILED |
| refunds[].requestedBy | String | MEMBER / SYSTEM / EXTERNAL / ADMIN |
| refunds[].reason | String / null | 환불 사유 |
| refunds[].portoneCancellationId | String / null | PortOne 취소 ID |
| refunds[].failureReason | String / null | 환불 실패 사유 |
| refunds[].requestedAt / completedAt | String / null | 요청 · 완료 시각 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | paymentId가 숫자가 아님 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 감사 기록 시점에 관리자 계정이 비활성화 상태 |
| 403 | AUTH-004 (ACCESS_DENIED) | 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름 |
| 404 | PAYMENT-001 (PAYMENT_NOT_FOUND) | 결제가 없음(0 · 음수 ID 포함) |
| 404 | ORDER-012 (ORDER_NOT_FOUND) | 결제에 대응하는 주문이 없음(정상 데이터에서는 발생하지 않음) |

### 비즈니스 규칙

- 결제가 없으면 PAYMENT-001로 끝나고 감사 기록도 남지 않습니다.
- 조회와 `PAYMENT_DETAIL_VIEW` 감사 기록을 한 쓰기 트랜잭션에서 처리합니다. 감사의 대상 ID는 paymentId, 마트 ID는 결제가 속한 **주문 당시의 마트**(회원의 현재 이용 마트가 아님)입니다. 감사 기록이 실패하면 결과를 반환하지 않습니다.
- 환불 멱등 키(Idempotency-Key)는 클라이언트 요청 식별용이라 운영 응답에 넣지 않습니다.
- 마트 거절 환불을 관리자가 재처리하면 새 환불 행이 생기므로, `refunds`에는 실패한 시도와 재시도가 모두 나옵니다.

---

## 15. 관리자 회원 포인트 조회

### API: 관리자 회원 포인트 조회

### Method / Path

`GET /api/admin/members/{memberId}/points`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| memberId | Path | Long | O | 회원 ID, 1 이상 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "포인트를 조회했습니다.",
  "data": {
    "memberId": 7,
    "availableBalance": 1250,
    "nearestExpiringAmount": 300,
    "nearestExpiresOn": "2026-11-30"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| memberId | Long | 요청한 회원 ID |
| availableBalance | Long | 오늘 실제로 쓸 수 있는 포인트: COMPLETED 적립 건 중 잔량이 있고 만료일이 오늘 이후(오늘 포함)인 잔량 합계 |
| nearestExpiringAmount | Long | 가장 이른 만료일의 사용 가능 잔량 합계. 없으면 0 |
| nearestExpiresOn | String / null | 가장 이른 만료일(`yyyy-MM-dd`). 없으면 null |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | memberId가 0 이하 |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | memberId가 숫자가 아님 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 감사 기록 시점에 관리자 계정이 비활성화 상태 |
| 403 | AUTH-004 (ACCESS_DENIED) | 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름 |

### 비즈니스 규칙

- 회원 포인트 조회(`GET /api/points`)와 같은 계산 · 응답 형식을 씁니다. 회원 존재 여부는 확인하지 않으며, 포인트 이력이 없는 회원(없는 회원 ID 포함)도 404가 아니라 0 · null로 응답합니다.
- 기준 날짜는 서버 Clock(Asia/Seoul)의 오늘입니다. 만료 배치가 아직 처리하지 않은 만료 잔량은 날짜 조건으로 계산에서만 빼고, 조회가 만료 처리 · 이력 저장 · 잔액 수정을 하지는 않습니다.
- 잔액과 가장 가까운 만료는 같은 읽기 시점의 값으로 맞춥니다.
- 잘못된 memberId는 조회 · 감사 기록 전에 입력 검증에서 끊습니다.
- 조회와 `POINT_BALANCE_VIEW` 감사 기록(대상 ID = memberId)을 한 쓰기 트랜잭션에서 처리하며, 감사 기록이 실패하면 결과를 반환하지 않습니다.

---

## 16. 관리자 회원 포인트 이력 조회

### API: 관리자 회원 포인트 이력 조회

### Method / Path

`GET /api/admin/members/{memberId}/points/history`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request

`GET /api/admin/members/7/points/history?orderId=1001&page=0&size=20`

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| memberId | Path | Long | O | 회원 ID, 1 이상 |
| orderId | Query | Long | X | 주문 ID, 1 이상. 지정하면 그 주문의 이력만 |
| page | Query | Integer | X | 0부터, 기본 0. 음수 · 숫자가 아닌 값은 0으로 처리 |
| size | Query | Integer | X | 기본 20, 최대 100(넘기면 100으로 잘림). 1 미만 · 숫자가 아닌 값은 20 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "포인트 이력을 조회했습니다.",
  "data": {
    "content": [
      {
        "historyId": 5003,
        "earningId": 401,
        "orderId": 1001,
        "type": "USE_CONFIRMED",
        "amount": 500,
        "createdAt": "2026-10-20T10:00:00+09:00"
      },
      {
        "historyId": 5002,
        "earningId": 401,
        "orderId": 1001,
        "type": "USE_RESERVED",
        "amount": 500,
        "createdAt": "2026-10-14T21:30:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].historyId | Long | 포인트 이력 ID |
| content[].earningId | Long | 움직인 포인트의 적립 건(lot) ID |
| content[].orderId | Long / null | 관련 주문 ID. 만료 배치 · 지연 만료 · 탈퇴 소멸은 주문이 없어 null일 수 있음 |
| content[].type | String | EARN_PENDING / EARN_COMPLETED / EARN_CANCELED / USE_RESERVED / USE_CONFIRMED / RESTORED / EXPIRED / WITHDRAWAL_FORFEITED |
| content[].amount | Long | 움직인 포인트(항상 양수, 방향은 type으로 구분) |
| content[].createdAt | String | 이력 생성 시각 |
| page / size / totalElements / totalPages | Integer / Integer / Long / Integer | 페이지 정보 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | memberId 또는 orderId가 0 이하 |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | memberId · orderId가 숫자가 아님 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 감사 기록 시점에 관리자 계정이 비활성화 상태 |
| 403 | AUTH-004 (ACCESS_DENIED) | 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름 |

### 비즈니스 규칙

- 최신순(`createdAt` 내림차순, 같은 시각은 `id` 내림차순) 고정입니다. 클라이언트가 보낸 `sort`는 버리고 page · size만 받아 다시 만듭니다.
- orderId 조건에는 항상 회원 조건이 함께 붙어, 다른 회원의 주문 ID를 넣으면 오류 없이 빈 페이지입니다. 이력이 없는 회원도 빈 페이지입니다.
- 적립 건은 이력과 함께 읽어 와 이력마다 추가 조회가 생기지 않습니다.
- 조회와 `POINT_HISTORY_VIEW` 감사 기록(대상 ID = memberId)을 한 쓰기 트랜잭션에서 처리하며, 감사 기록이 실패하면 결과를 반환하지 않습니다. orderId 등 조회 조건은 감사에 남기지 않습니다.

---

## 17. 관리자 멤버십 결제 시도 목록 조회

### API: 관리자 멤버십 결제 시도 목록 조회

### Method / Path

`GET /api/admin/membership-payment-attempts`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request (Query)

`GET /api/admin/membership-payment-attempts?status=MANUAL_REVIEW&membershipId=55&opsReviewOnly=true&blockedOnly=false&page=0&size=20`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| status | String | X | 시도 상태: READY / DISPATCHING / UNKNOWN / MANUAL_REVIEW / PAID / FAILED / CANCELED. 생략하면 전체 |
| membershipId | Long | X | 이용권 ID, 1 이상 |
| opsReviewOnly | Boolean | X | true면 운영 확인 표시(`opsReviewRequiredAt`)가 있는 시도만. 기본 false |
| blockedOnly | Boolean | X | true면 강한 정합성 차단 사유의 시도만. 기본 false |
| page | Integer | X | 0부터, 기본 0 |
| size | Integer | X | 기본 20, 1~100(범위 밖이면 COMMON-001) |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "멤버십 결제 시도를 조회했습니다.",
  "data": {
    "content": [
      {
        "attemptId": 901,
        "membershipPaymentId": 801,
        "membershipId": 55,
        "attemptNo": 1,
        "portonePaymentId": "mpay_4b1f0c2d9e8a47f6b3c5d7e9a1b2c3d4",
        "status": "MANUAL_REVIEW",
        "paymentStatus": "MANUAL_REVIEW",
        "requestedAt": "2026-11-01T09:00:00+09:00",
        "dispatchedAt": "2026-11-01T09:00:02+09:00",
        "completedAt": "2026-11-02T09:05:00+09:00",
        "pgPaidAt": null,
        "failureReason": "UNKNOWN_WINDOW_EXPIRED",
        "opsReviewRequiredAt": "2026-11-02T09:05:00+09:00",
        "opsReviewReason": "UNKNOWN_WINDOW_EXPIRED",
        "settlementBlocked": false,
        "refundRequiredAt": null,
        "graceDeadlineAt": null,
        "unknownWindowEndAt": "2026-11-02T09:00:02+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].attemptId | Long | 결제 시도 ID |
| content[].membershipPaymentId | Long | 결제 회차 ID |
| content[].membershipId | Long | 이용권 ID |
| content[].attemptNo | Integer | 회차 안의 시도 순번 |
| content[].portonePaymentId | String | 시도의 PortOne 결제 ID(`mpay_` + 하이픈 없는 UUID) |
| content[].status | String | 시도 상태(아래 표) |
| content[].paymentStatus | String | 회차 상태: PENDING / RETRY_WAITING / PAYMENT_METHOD_REQUIRED / UNKNOWN / MANUAL_REVIEW / PAID / FAILED |
| content[].requestedAt | String | 시도 접수 시각 |
| content[].dispatchedAt | String / null | PortOne 호출 선점(발송) 시각. 미발송이면 null |
| content[].completedAt | String / null | 시도 확정 시각 |
| content[].pgPaidAt | String / null | PG 결제 시각(신뢰 가능한 수납일 때) |
| content[].failureReason | String / null | 시도 실패 · 미확정 코드(예: PG_FAILED, PAYMENT_NOT_FOUND, UNKNOWN_WINDOW_EXPIRED). PG 자유 문장은 넣지 않음 |
| content[].opsReviewRequiredAt | String / null | 운영 확인 표시 시각 |
| content[].opsReviewReason | String / null | 운영 확인 사유(`MembershipOpsReviewReason`, 아래 표) |
| content[].settlementBlocked | Boolean | opsReviewReason이 강한 정합성 차단 사유면 true |
| content[].refundRequiredAt | String / null | 반영하지 않은 수납이라 SYSTEM 환불이 필요하다고 표시한 시각 |
| content[].graceDeadlineAt | String / null | 회차의 결제 마감(유예) 시각. 갱신 회차에 사용 |
| content[].unknownWindowEndAt | String / null | 결과 불명 창 종료 시각 = dispatchedAt + 결과 불명 창(기본 24시간). 미발송이면 null |
| page / size / totalElements / totalPages | Integer / Integer / Long / Integer | 페이지 정보 |

| 시도 상태 `status` | 설명 |
| --- | --- |
| READY | 접수됨. 아직 PortOne을 호출하지 않음 |
| DISPATCHING | 선점됨. PortOne 호출 중 |
| UNKNOWN | 호출 결과 불명. 같은 결제 ID로 재조회 |
| MANUAL_REVIEW | 재조회로도 확정 불가. 운영 확인 |
| PAID | 결제 성공 확정 |
| FAILED | 결제 실패 확정 |
| CANCELED | PortOne 호출 전에 접은 시도(회차 종료 등) |

| 운영 확인 사유 `opsReviewReason` | 구분 |
| --- | --- |
| LATE_PAID_AFTER_DEADLINE, PAID_CONFLICT, PAID_ON_CLOSED_PAYMENT, FIRST_PAYMENT_PERIOD_ENDED, FIRST_PAYMENT_LONG_DELAY, RENEWAL_PERIOD_ENDED, RENEWAL_LONG_DELAY, SYSTEM_REFUND_FAILED, PAID_AMOUNT_MISMATCH, LATE_PAID_AFTER_FAILED, PAID_AFTER_WITHDRAWAL, CONFLICTING_MEMBERSHIP, UNKNOWN_WINDOW_EXPIRED | 차단 아님(안내 · 환불 필요 · 결과 확인 대기) |
| APPLIED_STATE_INCONSISTENT, UNDISPATCHED_PAID, UNDISPATCHED_PG_ACTIVITY, PAID_EVIDENCE_CONFLICT, PAID_AT_INVALID, REFUND_EVIDENCE_CONFLICT, REFUND_TARGET_PERIOD_MISMATCH | 강한 정합성 차단(`settlementBlocked = true`, `blockedOnly` 필터 대상) |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | 없는 status 값, membershipId 0 이하, page 음수, size 1~100 밖, Boolean · 숫자 파라미터 형식 오류 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 감사 기록 시점에 관리자 계정이 비활성화 상태 |
| 403 | AUTH-004 (ACCESS_DENIED) | 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름 |

### 비즈니스 규칙

- 정렬은 시도 ID 내림차순(최신 시도부터) 고정이며 정렬 파라미터는 받지 않습니다.
- 필터는 모두 AND입니다. status를 생략하면 모든 상태 값을 넘겨 전체를 조회합니다.
- 조회와 `MEMBERSHIP_PAYMENT_LIST_VIEW` 감사 기록을 한 쓰기 트랜잭션에서 처리하며, 감사 기록이 실패하면 결과를 반환하지 않습니다.
- 빌링키(원문 · ID), 고객 정보, PG 응답 원문은 응답에 넣지 않습니다.
- 운영 확인 사유 · 차단의 의미와 판정 규칙은 [api/membership-reconciliation.md](api/membership-reconciliation.md)(강한 정합성 차단 · 결과 불명 창)를 따릅니다.

---

## 18. 관리자 멤버십 결제 시도 상세 조회

### API: 관리자 멤버십 결제 시도 상세 조회

### Method / Path

`GET /api/admin/membership-payment-attempts/{attemptId}`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| attemptId | Path | Long | O | 결제 시도 ID |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "멤버십 결제 시도 상세를 조회했습니다.",
  "data": {
    "attempt": {
      "attemptId": 902,
      "membershipPaymentId": 802,
      "membershipId": 56,
      "attemptNo": 1,
      "portonePaymentId": "mpay_9d8c7b6a5f4e43d2a1b0c9d8e7f6a5b4",
      "status": "PAID",
      "paymentStatus": "PAID",
      "requestedAt": "2026-10-01T10:00:00+09:00",
      "dispatchedAt": "2026-10-01T10:00:01+09:00",
      "completedAt": "2026-10-01T10:00:05+09:00",
      "pgPaidAt": "2026-10-01T10:00:03+09:00",
      "failureReason": null,
      "opsReviewRequiredAt": null,
      "opsReviewReason": null,
      "settlementBlocked": false,
      "refundRequiredAt": null,
      "graceDeadlineAt": null,
      "unknownWindowEndAt": "2026-10-02T10:00:01+09:00"
    },
    "periodStartDate": "2026-10-01",
    "periodEndDate": "2026-10-31",
    "nextRetryAt": null,
    "paymentFailureReason": null,
    "membershipAppliedAt": "2026-10-01T10:00:05+09:00",
    "systemRefunds": [],
    "refunds": [
      {
        "refundId": 611,
        "requestedBy": "MEMBER",
        "amount": 3300,
        "status": "COMPLETED",
        "reason": "MEMBER_TERMINATION",
        "requestedAt": "2026-10-10T14:00:00+09:00",
        "completedAt": "2026-10-10T14:00:04+09:00",
        "portoneCancellationId": "cancel_0f1e2d3c",
        "firstDispatchedAt": "2026-10-10T14:00:01+09:00",
        "nextCheckAt": null,
        "reviewRequired": false,
        "opsReviewReason": null,
        "opsReviewRequiredAt": null,
        "retryOfRefundId": null,
        "periodDays": 31,
        "usedDays": 10,
        "paidAmountSnapshot": 4900
      }
    ]
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| attempt | Object | 목록의 한 줄과 같은 구조(17번 응답 표) |
| periodStartDate / periodEndDate | String / null | 회차의 결제 기간 시작 · 종료일(`yyyy-MM-dd`) |
| nextRetryAt | String / null | 회차의 다음 재시도 시각 |
| paymentFailureReason | String / null | 회차 실패 사유 코드 |
| membershipAppliedAt | String / null | 이용권에 반영(활성화 · 연장)된 시각 |
| systemRefunds | Array | 이 시도의 SYSTEM 환불만, 기존 5필드 형태(ID 오름차순) |
| systemRefunds[].refundId / amount | Long | 환불 ID · 금액 |
| systemRefunds[].status | String | REQUESTED / COMPLETED / FAILED |
| systemRefunds[].requestedAt / completedAt | String / null | 요청 · 완료 시각 |
| refunds | Array | 이 시도의 환불 행 전부(MEMBER · SYSTEM, ID 오름차순) |
| refunds[].refundId | Long | 환불 ID |
| refunds[].requestedBy | String | MEMBER(즉시 해지) / SYSTEM(반영하지 않은 수납 반환) |
| refunds[].amount | Long | 환불 금액 |
| refunds[].status | String | REQUESTED / COMPLETED / FAILED |
| refunds[].reason | String / null | MEMBER는 `MEMBER_TERMINATION`, SYSTEM은 환불 사유 코드(예: LATE_PAID_AFTER_FAILED) |
| refunds[].requestedAt / completedAt | String / null | 요청 · 완료 시각 |
| refunds[].portoneCancellationId | String / null | PortOne 취소 ID |
| refunds[].firstDispatchedAt | String / null | 최초 취소 요청 발송 시각 |
| refunds[].nextCheckAt | String / null | 다음 결과 확인 예약 시각 |
| refunds[].reviewRequired | Boolean | 강한 차단이거나, REQUESTED인데 운영 확인 사유가 있으면 true. 정상 확정된 행의 과거 표시는 false |
| refunds[].opsReviewReason | String / null | 환불 운영 확인 사유(`MembershipRefundOpsReason`, 20번 표) |
| refunds[].opsReviewRequiredAt | String / null | 운영 확인 표시 시각 |
| refunds[].retryOfRefundId | Long / null | 관리자 재실행으로 만든 후속 행이면 원래 실패 환불 ID |
| refunds[].periodDays | Integer / null | MEMBER 환불의 결제 기간 일수. SYSTEM은 null |
| refunds[].usedDays | Integer / null | MEMBER 환불의 이용 일수. SYSTEM은 null |
| refunds[].paidAmountSnapshot | Long / null | 원래 수납액(PG 식별 검증 기준) |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | attemptId가 숫자가 아님 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 감사 기록 시점에 관리자 계정이 비활성화 상태 |
| 403 | AUTH-004 (ACCESS_DENIED) | 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름 |
| 404 | BILLING-003 (MEMBERSHIP_PAYMENT_NOT_FOUND) | 시도가 없음 |

### 비즈니스 규칙

- 시도가 없으면 BILLING-003으로 끝나고 감사 기록도 남지 않습니다.
- 조회와 `MEMBERSHIP_PAYMENT_DETAIL_VIEW` 감사 기록(대상 ID = attemptId)을 한 쓰기 트랜잭션에서 처리하며, 감사 기록이 실패하면 결과를 반환하지 않습니다.
- `systemRefunds`(기존 형태)와 `refunds`(확장 형태)는 같은 조회 결과로 조립합니다. 환불 행이 하나도 없는 시도에 PG 취소가 관측되면 외부 취소로 봅니다.
- 빌링키 · 고객 정보 · PG 응답 원문은 넣지 않습니다. 환불 정책(계산 · 확정 규칙)은 [api/membership-refund.md](api/membership-refund.md)를 따릅니다.

---

## 19. 관리자 멤버십 결제 시도 재조회

### API: 관리자 멤버십 결제 시도 재조회

### Method / Path

`POST /api/admin/membership-payment-attempts/{attemptId}/recheck`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| attemptId | Path | Long | O | 결제 시도 ID |

본문 없음. 결제 ID · 금액 · 상태를 입력받지 않습니다.

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "PortOne 결제 상태를 다시 확인했습니다.",
  "data": {
    "attemptId": 901,
    "outcome": "ACTIVATED",
    "status": "PAID",
    "paymentStatus": "PAID",
    "opsReviewReason": "UNKNOWN_WINDOW_EXPIRED",
    "settlementBlocked": false,
    "blockedReason": null,
    "pgVerdictKind": "PAID",
    "pgPaidAt": "2026-11-01T09:00:04+09:00",
    "pgPaidAmount": 4900
  }
}
```

차단 회차의 재조회 예시(조회만 하고 반영하지 않음)

```json
{
  "success": true,
  "code": "OK",
  "message": "PortOne 결제 상태를 다시 확인했습니다.",
  "data": {
    "attemptId": 905,
    "outcome": "RECONCILE_HALTED",
    "status": "PAID",
    "paymentStatus": "PAID",
    "opsReviewReason": "PAID_EVIDENCE_CONFLICT",
    "settlementBlocked": true,
    "blockedReason": "PAID_EVIDENCE_CONFLICT",
    "pgVerdictKind": "UNTRUSTED",
    "pgPaidAt": null,
    "pgPaidAmount": null
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| attemptId | Long | 결제 시도 ID |
| outcome | String | 이번 재조회의 반영 결과(`MembershipChargeOutcome`, 아래 표) |
| status | String | 재조회 뒤 시도 상태 |
| paymentStatus | String | 재조회 뒤 회차 상태 |
| opsReviewReason | String / null | 재조회 뒤 시도의 운영 확인 사유 |
| settlementBlocked | Boolean | 재조회 뒤 강한 정합성 차단 여부 |
| blockedReason | String / null | settlementBlocked면 opsReviewReason과 같은 값, 아니면 null |
| pgVerdictKind | String / null | 이번 PortOne 조회 판정: PAID / PAID_AMOUNT_MISMATCH / FAILED / UNRESOLVED / NOT_FOUND / UNTRUSTED. 조회 결과가 없으면 null |
| pgPaidAt | String / null | 신뢰 가능한 수납(PAID · PAID_AMOUNT_MISMATCH)일 때만 PG 결제 시각 |
| pgPaidAmount | Long / null | 신뢰 가능한 수납일 때만 PG 실제 수납액 |

| outcome | 의미 |
| --- | --- |
| ACTIVATED | 첫 결제 성공 확정 → 이용권 활성화 |
| EXTENDED | 갱신 성공 확정 → 이용 기간 연장 · 회차 PAID |
| CLOSED_UNPAID | 조회로 확정된 실패 → 결제 대기 이용권 종료 |
| RETRY_WAITING | 갱신 실패 확정 → 같은 회차 재시도 대기 |
| RENEWAL_STOPPED | 갱신 최종 실패 · 마감 · 해지 → 회차 FAILED, 자동갱신 끔 |
| PAYMENT_METHOD_REQUIRED | 갱신에 쓸 ACTIVE 카드 없음 → 카드 등록 대기 |
| UNAPPLIED_REFUND_REQUESTED | 수납은 확인됐지만 반영하지 않음 → SYSTEM 환불 REQUESTED |
| STILL_UNKNOWN | 결과 불명 유지(조회 장애 · 404 등). 상태 변경 없음 또는 UNKNOWN 유지 |
| MANUAL_REVIEW | 신뢰할 수 없는 결과이거나 결과 불명 창이 끝남 → 운영 확인 |
| IN_PROGRESS | 다른 실행자가 PortOne 호출 중(임대 안)이거나 조회 사이 선점이 바뀜. 반영 없음 |
| ALREADY_APPLIED | 이미 확정된 시도(멱등). 변경 없음 |
| NOT_DISPATCHED | PG에 보낸 적 없는 READY이고 PG에도 거래가 없음 |
| RETRY_READY | READY 만료 → 새 시도 READY |
| RECONCILE_HALTED | 강한 정합성 차단 회차이거나 이번 판정에서 차단됨. 반영 없음 |
| REFUND_RECONCILED | 수납 PAID 시도의 취소 관측을 환불 정합화가 완료 · 실패로 확정 |
| REFUND_RECONCILIATION_PENDING | 환불 정합화 미확정 |
| REFUND_RECONCILIATION_REVIEW_REQUIRED | 환불 행 없는 취소(외부 취소) · 환불 운영 확인 · 차단 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | attemptId가 숫자가 아님 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 감사 기록 시점에 관리자 계정이 비활성화 상태(PortOne 호출 전) |
| 403 | AUTH-004 (ACCESS_DENIED) | 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름(PortOne 호출 전) |
| 404 | BILLING-003 (MEMBERSHIP_PAYMENT_NOT_FOUND) | 시도가 없음(감사 · PortOne 호출 없음), 또는 결과 반영 중 회차가 없음(정합성 오류) |
| 404 | MEMBERSHIP-006 (ACTIVE_MEMBERSHIP_NOT_FOUND) | 시도의 이용권이 없거나 회원 소유가 맞지 않음(정합성 오류) |
| 404 | MEMBER-001 (MEMBER_NOT_FOUND) | 정산 잠금 대상 회원 행이 없음(정합성 오류. 탈퇴 회원은 해당하지 않음) |
| 409 | COMMON-010 (LOCK_ACQUISITION_TIMEOUT) | 결과 반영 트랜잭션의 회원 · 이용권 · 회차 · 시도 락 대기 시간 초과 |
| 500 | COMMON-500 (INTERNAL_SERVER_ERROR) | 시도 · 회차 · 이용권 연결이 맞지 않는 등 예상하지 못한 상태 |

PortOne 조회 실패(404 · 5xx · timeout)는 오류로 응답하지 않고 200 + `pgVerdictKind`(NOT_FOUND · UNRESOLVED)와 `outcome`으로 알립니다.

### 비즈니스 규칙

- 처리 순서는 `요청 접수 감사 커밋 → PortOne GET(트랜잭션 밖) → 결과 반영 TX → 최신 상태 응답`입니다.
  1. 별도 짧은 트랜잭션에서 시도 존재를 확인하고 `MEMBERSHIP_PAYMENT_RECHECK` 감사(대상 ID = attemptId)를 기록 · 커밋합니다. 실패하면 PortOne을 부르지 않습니다. 이후 단계가 실패해도 이 감사 기록은 남습니다.
  2. 시도 ID로 이용권을, 이용권으로 회원을 역조회합니다(트랜잭션 밖).
  3. 같은 결제 ID로 `GET /payments/{id}`를 **한 번만** 호출합니다. 어떤 경우에도 POST(새 청구)는 하지 않고, 새 결제 ID도 만들지 않습니다.
  4. 결과 반영 TX는 회원(정산 잠금, 탈퇴 회원 포함) → 이용권 → 회차 → 회차의 시도 전부(PK 오름차순) 순으로 잠근 뒤 판정합니다.
- 진입점은 `Propagation.NEVER`라 바깥 트랜잭션 안에서 호출되면 PortOne 요청 전에 거절됩니다. 락을 잡은 채 외부 호출을 하지 않습니다.
- 운영자 재조회(`ADMIN_RECHECK`)는 복구 스케줄러와 달리 READY · 신선한 DISPATCHING · MANUAL_REVIEW · 확정 시도도 모두 조회합니다. MANUAL_REVIEW 시도라도 신뢰 가능한 PAID · FAILED가 확인되면 정상 판정 흐름으로 확정합니다.
- 회차에 강한 정합성 차단 시도가 하나라도 있으면 조회만 하고 반영하지 않으며 `outcome = RECONCILE_HALTED`와 이번 관측값을 돌려줍니다. 차단 표시 해제 · 강제 PAID API는 없습니다.
- 결과 불명(UNRESOLVED · NOT_FOUND)인데 발송 시각 + 결과 불명 창(기본 24시간)이 지났으면 시도 · 회차를 MANUAL_REVIEW + `UNKNOWN_WINDOW_EXPIRED`로 바꿉니다(차단 아님).
- 결과 반영 중 정합성 오류가 확인되면 반영 TX를 롤백하고 별도 TX로 강한 차단 표시를 남긴 뒤 `RECONCILE_HALTED`를 돌려줍니다(오류 응답 아님).
- 반영 결과가 SYSTEM 환불 요청이면 커밋 뒤 환불 실행을 큐에 예약합니다. 수납 PAID 시도에 취소가 관측되면 같은 응답으로 환불 정합화를 호출합니다(추가 GET · 취소 POST 없음).
- 응답에는 PG 응답 원문 · 실패 문장 · 취소 내역 · 빌링키 · 고객 정보를 넣지 않습니다.
- 상태 × 관측별 상세 판정표는 [api/membership-reconciliation.md](api/membership-reconciliation.md)(재조회 원인과 GET 허용 · 결과 반영 판정 순서)를 따릅니다.

---

## 20. 관리자 멤버십 환불 재조회

### API: 관리자 멤버십 환불 재조회

### Method / Path

`POST /api/admin/membership-refunds/{refundId}/recheck`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| refundId | Path | Long | O | 멤버십 환불 ID(MEMBER · SYSTEM 모두 가능) |

본문 없음.

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "PortOne 환불 상태를 다시 확인했습니다.",
  "data": {
    "refundId": 612,
    "attemptId": 903,
    "requestedBy": "SYSTEM",
    "status": "COMPLETED",
    "amount": 4900,
    "outcome": "COMPLETED",
    "reviewRequired": false,
    "opsReviewReason": null,
    "portoneCancellationId": "cancel_5e6f7a8b",
    "completedAt": "2026-11-03T11:20:05+09:00",
    "retryOfRefundId": null,
    "pgLookedUp": true,
    "pgPaymentStatus": "CANCELLED",
    "pgCancellationId": "cancel_5e6f7a8b",
    "pgCancellationStatus": "SUCCEEDED",
    "pgCancellationAmount": 4900,
    "pgLookupCode": null
  }
}
```

PortOne 조회 실패 예시

```json
{
  "success": true,
  "code": "OK",
  "message": "PortOne 환불 상태를 다시 확인했습니다.",
  "data": {
    "refundId": 613,
    "attemptId": 904,
    "requestedBy": "MEMBER",
    "status": "REQUESTED",
    "amount": 3300,
    "outcome": "PENDING",
    "reviewRequired": false,
    "opsReviewReason": null,
    "portoneCancellationId": null,
    "completedAt": null,
    "retryOfRefundId": null,
    "pgLookedUp": false,
    "pgPaymentStatus": null,
    "pgCancellationId": null,
    "pgCancellationStatus": null,
    "pgCancellationAmount": null,
    "pgLookupCode": "COMMON-009"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| refundId | Long | 환불 ID |
| attemptId | Long | 환불 대상 결제 시도 ID |
| requestedBy | String | MEMBER / SYSTEM |
| status | String | 재조회 뒤 저장된 환불 상태: REQUESTED / COMPLETED / FAILED |
| amount | Long | 환불 금액 |
| outcome | String | 이번 재조회의 반영 결과: COMPLETED / FAILED / PENDING / REVIEW_REQUIRED(아래 표) |
| reviewRequired | Boolean | 강한 차단이거나 REQUESTED + 운영 확인 사유면 true |
| opsReviewReason | String / null | 환불 운영 확인 사유(아래 표) |
| portoneCancellationId | String / null | 저장된 PortOne 취소 ID |
| completedAt | String / null | 환불 완료 시각 |
| retryOfRefundId | Long / null | 관리자 재실행 후속 행이면 원래 실패 환불 ID |
| pgLookedUp | Boolean | 이번 PortOne 조회가 성공했으면 true. 실패하면 저장 상태가 COMPLETED여도 false |
| pgPaymentStatus | String / null | 이번 조회의 PortOne 결제 상태: READY / PENDING / VIRTUAL_ACCOUNT_ISSUED / PAID / FAILED / PARTIAL_CANCELLED / CANCELLED / UNKNOWN |
| pgCancellationId | String / null | 이 환불에 매칭된 취소 내역의 ID(저장 취소 ID 또는 서버가 만든 요청 사유로 매칭). 매칭이 없으면 null |
| pgCancellationStatus | String / null | 매칭된 취소 상태: SUCCEEDED / REQUESTED / FAILED / UNKNOWN |
| pgCancellationAmount | Long / null | 매칭된 취소 금액 |
| pgLookupCode | String / null | 조회 실패 코드: PAYMENT-001(PortOne 404) / COMMON-008(오류 응답 · 연결 실패 · 해석 실패) / COMMON-009(timeout). 성공이면 null |

| outcome | 의미 |
| --- | --- |
| COMPLETED | 매칭된 취소 성공을 확인해 COMPLETED로 확정(MEMBER 환불은 같은 TX에서 이용권 종료). 이미 COMPLETED인 행의 같은 근거 재확인 포함 |
| FAILED | 식별 · 금액이 맞는 FAILED 취소를 확인해 FAILED로 확정. 이미 FAILED인 행의 멱등 확인 포함 |
| PENDING | 조회 실패, 처리 중, 매칭되는 취소 없음, 해석할 수 없는 취소 목록 등. REQUESTED 유지 |
| REVIEW_REQUIRED | 강한 차단(이미 차단된 행 · 회차, 또는 이번 관측으로 모순 확인) 또는 자동 중단 상태의 REQUESTED 행 |

| 환불 운영 확인 사유 `opsReviewReason` | 구분 |
| --- | --- |
| UNKNOWN_WINDOW_EXPIRED, PG_CONFIG_ERROR, PG_AUTH_ERROR, PG_REQUEST_REJECTED, CANCELLATION_STATUS_UNKNOWN, LEGACY_DISPATCH_UNPROVEN | 자동 중단(REQUESTED 유지, 자동 복구 · 재POST만 멈춤) |
| EVIDENCE_CONFLICT, TARGET_PERIOD_MISMATCH, PG_IDENTITY_MISMATCH | 강한 정합성 차단 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | refundId가 숫자가 아님 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 감사 기록 시점에 관리자 계정이 비활성화 상태(PortOne 호출 전) |
| 403 | AUTH-004 (ACCESS_DENIED) | 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름(PortOne 호출 전) |
| 404 | BILLING-020 (MEMBERSHIP_REFUND_NOT_FOUND) | 환불이 없음(감사 · PortOne 호출 없음) |
| 404 | BILLING-003 (MEMBERSHIP_PAYMENT_NOT_FOUND) | 결과 반영 중 시도 · 회차가 없음(정합성 오류) |
| 404 | MEMBERSHIP-006 (ACTIVE_MEMBERSHIP_NOT_FOUND) | 환불의 이용권이 없거나 회원 소유가 맞지 않음(정합성 오류) |
| 404 | MEMBER-001 (MEMBER_NOT_FOUND) | 정산 잠금 대상 회원 행이 없음(정합성 오류) |
| 409 | COMMON-010 (LOCK_ACQUISITION_TIMEOUT) | 결과 반영 트랜잭션의 락 대기 시간 초과 |
| 500 | COMMON-500 (INTERNAL_SERVER_ERROR) | 환불 · 시도 · 회차 연결이 맞지 않는 등 예상하지 못한 상태 |

PortOne 조회 실패는 오류로 응답하지 않고 200 + `pgLookedUp = false`, `pgLookupCode`, `outcome = PENDING`으로 알립니다.

### 비즈니스 규칙

- 처리 순서는 `요청 접수 감사 커밋 → PortOne GET 1회(트랜잭션 밖) → 결과 반영 TX → 최신 상태 응답`입니다. 감사(`MEMBERSHIP_REFUND_RECHECK`, 대상 ID = refundId)는 환불 존재를 확인한 짧은 트랜잭션에서 기록 · 커밋하며, 실패하면 PortOne을 부르지 않습니다.
- 취소 POST는 하지 않습니다. 실행권 · 토큰 없이 조회 결과만 반영하므로, 매칭되는 취소가 없어 POST가 가능한 상황이어도 `PENDING`만 돌려주고 새 요청을 보내지 않습니다.
- 조회 실패 시에는 DB를 바꾸지 않고(다음 확인 예약 · 운영 표시 없음) `PENDING`을 돌려줍니다.
- 환불 행이 이미 강한 차단이면 조회 결과를 관측값으로만 돌려주고(`outcome = REVIEW_REQUIRED`) 자동 반영 · 차단 해제를 하지 않습니다. 같은 회차의 결제 시도가 차단이어도 반영하지 않습니다.
- 결과 반영 TX는 회원(정산 잠금) → 이용권 → 회차 → 시도(PK 오름차순) → 환불(PK 오름차순) 순으로 잠그고, 한 번의 관측으로 그 시도의 환불 행 전부를 평가합니다. 응답의 outcome은 요청한 환불 한 건의 결과입니다.
- 토큰이 없는 경로라 해석할 수 없는 취소 목록 · 발송 이력 불명 신호를 운영 표시로 바꾸지 않고 `PENDING`으로 돌려줍니다.
- PortOne 결제 상태 값은 PortOne 철자(`CANCELLED`, `PARTIAL_CANCELLED`)로 직렬화됩니다.
- 응답에는 PG 원문 · 고객 정보 · 기간 값(이용 일수 등)을 넣지 않습니다. 매칭 · 확정 규칙은 [api/membership-refund.md](api/membership-refund.md)(실행 · 결과 반영 · 운영 확인 코드)를 따릅니다.

---

## 21. 관리자 멤버십 환불 재실행

### API: 관리자 멤버십 환불 재실행

### Method / Path

`POST /api/admin/membership-refunds/{refundId}/retry`

### 인증

- 관리자 인증 필요(SUPER_ADMIN 전용)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

Idempotency-Key 헤더는 받지 않습니다. 같은 실패 환불에 대한 반복 요청은 후속 행(`retryOfRefundId` UNIQUE)으로 멱등 처리합니다.

### Request

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| refundId | Path | Long | O | 재실행할 **FAILED SYSTEM** 환불 ID |

본문 없음. 금액 · 결제 ID · 취소 ID를 입력받지 않습니다.

### Response `200 OK`

새 후속 행을 만들어 실행한 경우

```json
{
  "success": true,
  "code": "OK",
  "message": "환불 재실행을 접수했습니다.",
  "data": {
    "refundId": 615,
    "retryOfRefundId": 614,
    "existing": false,
    "status": "COMPLETED",
    "amount": 4900,
    "executionResult": "COMPLETED",
    "reviewRequired": false,
    "opsReviewReason": null,
    "portoneCancellationId": "cancel_9a0b1c2d"
  }
}
```

이미 후속 행이 있는 경우(재요청)

```json
{
  "success": true,
  "code": "OK",
  "message": "환불 재실행을 접수했습니다.",
  "data": {
    "refundId": 615,
    "retryOfRefundId": 614,
    "existing": true,
    "status": "REQUESTED",
    "amount": 4900,
    "executionResult": null,
    "reviewRequired": false,
    "opsReviewReason": null,
    "portoneCancellationId": null
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| refundId | Long | 후속 환불 ID(새로 만든 것 또는 이미 있던 것) |
| retryOfRefundId | Long | 원래 실패 환불 ID(요청한 refundId) |
| existing | Boolean | 이미 있던 후속 행을 돌려줬으면 true |
| status | String | 응답 시점의 후속 환불 상태: REQUESTED / COMPLETED / FAILED |
| amount | Long | 환불 금액(원래 실패 환불과 같음) |
| executionResult | String / null | 이번 요청의 동기 실행 1회 결과: COMPLETED / FAILED / PENDING / REVIEW_REQUIRED / SKIPPED. existing이면 실행하지 않아 null |
| reviewRequired | Boolean | 강한 차단이거나 REQUESTED + 운영 확인 사유면 true |
| opsReviewReason | String / null | 환불 운영 확인 사유(20번 표) |
| portoneCancellationId | String / null | PortOne 취소 ID |

| executionResult | 의미 |
| --- | --- |
| COMPLETED | 취소 성공을 조회로 확인해 COMPLETED |
| FAILED | 식별 · 금액이 맞는 FAILED 취소를 확인해 FAILED |
| PENDING | 결과 불명 · 처리 중. REQUESTED 유지, 다음 확인 예약 |
| REVIEW_REQUIRED | 운영 확인 표시(PortOne 400 · 401 · 403 거절, 설정 오류, 결과 확인 창 종료, 강한 차단 등). REQUESTED 유지 |
| SKIPPED | 실행권을 잡지 못함(이미 확정 · 다른 실행자 임대 중 등). 큐 · 복구 스캔이 이어서 처리 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-002 (INVALID_REQUEST_FORMAT) | refundId가 숫자가 아님 |
| 403 | AUTH-006 (SUPER_ADMIN_ONLY) | MART_ADMIN 토큰으로 호출 |
| 403 | AUTH-009 (INACTIVE_ADMIN) | 새 후속 행 접수의 감사 기록 시점에 관리자 계정이 비활성화 상태(후속 행도 롤백, PortOne 호출 없음) |
| 403 | AUTH-004 (ACCESS_DENIED) | 새 후속 행 접수의 감사 기록 시점에 관리자 행이 없거나 토큰의 역할 · 마트가 현재 관리자 정보와 다름(후속 행 롤백) |
| 404 | BILLING-020 (MEMBERSHIP_REFUND_NOT_FOUND) | 환불이 없음 |
| 404 | BILLING-003 (MEMBERSHIP_PAYMENT_NOT_FOUND) | 환불의 회차가 없음(정합성 오류) |
| 404 | MEMBERSHIP-006 (ACTIVE_MEMBERSHIP_NOT_FOUND) | 환불의 이용권이 없거나 회원 소유가 맞지 않음(정합성 오류) |
| 404 | MEMBER-001 (MEMBER_NOT_FOUND) | 정산 잠금 대상 회원 행이 없음(정합성 오류) |
| 409 | BILLING-021 (MEMBERSHIP_REFUND_RETRY_NOT_ALLOWED) | 후속 행이 없고 재실행 조건 불충족: MEMBER 환불, FAILED가 아님, 환불 · 시도 · 같은 시도의 다른 환불에 강한 차단, 같은 시도에 REQUESTED 또는 COMPLETED 환불 존재, 같은 시도의 SYSTEM 환불 금액이 서로 다름 |
| 409 | COMMON-010 (LOCK_ACQUISITION_TIMEOUT) | 접수 · 실행 트랜잭션의 락 대기 시간 초과 |
| 500 | COMMON-500 (INTERNAL_SERVER_ERROR) | 환불 · 시도 연결 불일치 등 예상하지 못한 상태. 접수 커밋 뒤 실행 중에 나면 후속 행은 REQUESTED로 남고 복구 스캔이 이어 처리 |

PortOne 취소 · 조회 실패는 오류로 응답하지 않고 200 + `executionResult`(PENDING · REVIEW_REQUIRED)로 알립니다.

### 비즈니스 규칙

- 처리 순서:
  1. 트랜잭션 밖에서 환불(BILLING-020) → 이용권의 회원 ID(MEMBERSHIP-006)를 역조회합니다.
  2. 접수 TX: 첫 DB 작업으로 회원을 정산 잠금(탈퇴 회원 포함)한 뒤 이용권 → 회차 → 시도 전부 → 그 시도의 환불 전부를 잠급니다.
  3. 원래 실패 환불을 가리키는 후속 행이 이미 있으면 상태와 관계없이 그 행을 돌려줍니다(`existing = true`). 검증 · 감사 · 실행을 다시 하지 않습니다.
  4. 없으면 재실행 조건을 검증하고(불충족 BILLING-021, 감사 없음), 원래 행과 같은 금액 · 사유 · 원수납액으로 새 REQUESTED SYSTEM 환불 행(`retryOfRefundId` = 원래 ID, 새 PG 멱등 키)을 저장합니다.
  5. 같은 TX에서 `MEMBERSHIP_REFUND_RETRY` 감사(대상 ID = 원래 실패 환불 ID)를 기록하고 커밋합니다. 감사 실패 시 후속 행까지 롤백되고 PortOne 호출은 없습니다.
  6. 커밋 뒤 환불 실행을 큐에 예약(실패해도 로그만, DB 복구 스캔이 받음)하고, 트랜잭션 밖에서 동기 실행을 1회 시도합니다(실행권 → GET → 필요 시 같은 키로 취소 POST → GET → 결과 반영).
- 감사는 **새 후속 행이 생길 때만** 남깁니다. 기존 후속 행 반환과 거절은 새 실행이 아니라 기록하지 않습니다.
- 동시에 같은 실패 환불을 재실행해도 회원 잠금이 직렬화하고, 뒤의 요청은 앞 요청의 후속 행을 `existing = true`로 받습니다. `retry_of_refund_id` UNIQUE가 최종 방어입니다.
- 최종 COMPLETED · FAILED는 POST 응답이 아니라 GET 결과로만 확정합니다. 동기 실행에서 확정되지 않으면 응답 status는 REQUESTED이고 큐 · 복구 스캔이 결과 확인을 이어 갑니다.
- 후속 행이 다시 FAILED가 되면 그 후속 행 ID로 이 API를 다시 호출해 재실행할 수 있습니다.
- MEMBER 환불의 재실행, 표시 해제 · 강제 완료 API는 없습니다. 실행 · 재POST 창 · 결과 확인 창 정책은 [api/membership-refund.md](api/membership-refund.md)를 따릅니다.

---

## 부록. 결제 도메인 에러 코드

`PaymentErrorCode`, `BillingKeyDeletionErrorCode`의 전체 상수입니다. 발생 API의 번호는 이 문서의 API 번호이며, 1~21 밖의 API는 경로로 적습니다.

| 코드 | HTTP | 메시지 | 발생 API |
| --- | --- | --- | --- |
| PAYMENT-001 | 404 | 결제 정보를 찾을 수 없습니다. | 2(PortOne 결제 404 포함), 3, 4, 14, 주문 거절(`POST /api/admin/orders/{orderId}/reject`), 내부 흐름(결제 만료) |
| PAYMENT-002 | 400 | 결제가 아직 완료되지 않았습니다. | 2, 주문 거절 |
| PAYMENT-003 | 409 | 결제가 실패하여 종료되었습니다. | 2 |
| PAYMENT-004 | 400 | 결제 금액이 일치하지 않습니다. | 2 |
| PAYMENT-005 | 409 | 결제 가능 시간이 만료되었습니다. | 2 |
| PAYMENT-006 | 409 | 이미 종료된 결제입니다. | 2 |
| PAYMENT-007 | 409 | 현재 결제 상태에서는 요청을 처리할 수 없습니다. | 2, 3, 주문 거절, 내부 흐름(결제 · 환불 엔티티 전이 방어) |
| PAYMENT-008 | 409 | 배송 준비가 시작되어 결제를 취소할 수 없습니다. | 3 |
| PAYMENT-009 | 409 | 결제 취소가 거절되었습니다. | 3, 주문 거절 |
| PAYMENT-010 | 500 | 결제 공개 설정을 불러올 수 없습니다. | 1 |
| PAYMENT-011 | 500 | 웹훅 설정이 없어 요청을 처리할 수 없습니다. | 6 |
| PAYMENT-012 | 409 | 처리 중인 결제나 환불이 있어 탈퇴할 수 없습니다. | 회원 탈퇴(`POST /api/members/me/withdraw`) |
| PAYMENT-013 | 409 | 지원하지 않는 결제 통화입니다. 고객센터로 문의해 주세요. | 2 |
| POINT-001 | 409 | 사용 가능한 포인트가 부족합니다. | 주문 생성(`POST /api/orders`, 포인트 사용 예약) |
| POINT-002 | 409 | 안내된 포인트 잔액과 현재 잔액이 다릅니다. 잔액을 다시 확인해 주세요. | 회원 탈퇴 |
| POINT-003 | 409 | 적립 예정이거나 사용 중인 포인트가 있어 탈퇴할 수 없습니다. 주문·배송이 끝난 뒤 다시 시도해 주세요. | 회원 탈퇴 |
| BILLING-001 | 400 | 유효하지 않은 자동결제 수단입니다. | 8 |
| BILLING-002 | 409 | 이미 사용 중인 빌링키입니다. | 8 |
| BILLING-003 | 404 | 멤버십 결제 정보를 찾을 수 없습니다. | 18, 19, 20 · 21(정합성 오류), 회원 즉시 해지(`POST /api/memberships/{membershipId}/terminate`), 내부 흐름(웹훅 · 복구 · 갱신) |
| BILLING-004 | 409 | 처리 중인 멤버십 결제나 환불이 있어 탈퇴할 수 없습니다. | 회원 탈퇴 |
| BILLING-006 | 409 | 사용할 수 없는 카드입니다. 새 카드를 등록해 주세요. | 8 |
| BILLING-007 | 409 | 카드 등록 요청이 유효하지 않습니다. 카드 등록을 다시 시작해 주세요. | 8 |
| BILLING-008 | 503 | 카드 등록 기능이 아직 열리지 않았습니다. | 7, 8 |
| BILLING-009 | 503 | 멤버십 자동결제가 아직 열리지 않았습니다. | 멤버십 구독(`POST /api/memberships/subscribe`) |
| BILLING-020 | 404 | 멤버십 환불 정보를 찾을 수 없습니다. | 20, 21, 회원 즉시 해지 |
| BILLING-021 | 409 | 현재 상태에서는 환불을 다시 실행할 수 없습니다. | 21 |
| BILLING-022 | 409 | 결제 내역 확인이 필요해 지금은 해지할 수 없습니다. 고객센터로 문의해 주세요. | 회원 즉시 해지 |
| BILLING-030 | 409 | 자동결제 수단을 정리하고 있습니다. 정리가 끝난 뒤 탈퇴를 다시 요청해 주세요. | 회원 탈퇴 |
| BILLING-031 | 409 | 자동결제 수단 정리에 운영 확인이 필요합니다. 고객 지원에 문의해 주세요. | 회원 탈퇴 |
| BILLING-032 | 500 | 자동결제 수단 정리 설정을 확인해야 합니다. | 회원 탈퇴, 내부 흐름(교체 빌링키 정리 스케줄러) |

- BILLING-005, BILLING-010~019, BILLING-023~029는 정의되어 있지 않습니다(번호 공백).
- BILLING-008 · BILLING-009는 컨벤션의 HTTP 상태 범위(400~409, 500) 밖인 503입니다.

결제 API에서 함께 쓰는 다른 도메인 · 공통 코드

| 코드 | HTTP | 메시지 | 발생 API |
| --- | --- | --- | --- |
| AUTH-001 | 401 | 로그인이 필요합니다. | 1 · 6 제외 전체(토큰 없음) |
| AUTH-002 | 401 | 유효하지 않은 토큰입니다. | 1 · 6 제외 전체 |
| AUTH-003 | 401 | 만료된 토큰입니다. | 1 · 6 제외 전체 |
| AUTH-004 | 403 | 접근 권한이 없습니다. | 2~5 · 7~12(관리자 토큰), 13~21(회원 토큰, 감사 기록 시 관리자 정보 불일치) |
| AUTH-006 | 403 | 전체 관리자만 처리할 수 있습니다. | 13~21(MART_ADMIN 토큰) |
| AUTH-009 | 403 | 비활성화된 관리자 계정입니다. | 13~20, 21(새 후속 행 접수 시) |
| AUTH-010 | 401 | 웹훅 검증에 실패했습니다. | 6 |
| MEMBER-001 | 404 | 회원을 찾을 수 없습니다. | 7, 8, 19 · 20 · 21(정합성 오류) |
| MEMBERSHIP-006 | 404 | 이용 중인 멤버십이 없습니다. | 19, 20, 21(이용권 역조회 · 잠금 실패, 정합성 오류) |
| ORDER-012 | 404 | 주문을 찾을 수 없습니다. | 2, 3, 4, 14(결제에 대응하는 주문이 없는 정합성 오류) |
| COMMON-001 | 400 | 입력값이 올바르지 않습니다. | 2, 3, 8, 12, 13, 15, 16, 17(입력 검증, `errors` 포함) |
| COMMON-002 | 400 | 요청 형식이 올바르지 않습니다. | 2, 3, 4, 6, 8, 12, 14, 15, 16, 18~21(JSON 파싱 · 경로/쿼리 타입 오류) |
| COMMON-006 | 409 | 같은 요청을 처리하고 있습니다. 잠시 후 다시 확인해 주세요. | 3, 주문 거절 |
| COMMON-007 | 400 | Idempotency-Key 헤더가 필요합니다. | 3 |
| COMMON-008 | 500 | 외부 서비스 응답에 문제가 있습니다. | 2, 3, 7, 8 |
| COMMON-009 | 500 | 외부 서비스 응답이 지연되고 있습니다. | 2, 8 |
| COMMON-010 | 409 | 다른 요청이 같은 자원을 처리하고 있습니다. 잠시 후 다시 시도해 주세요. | 2, 3, 8, 19, 20, 21(락 대기 시간 초과) |
| COMMON-011 | 409 | 같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다. | 3, 주문 거절 |
| COMMON-500 | 500 | 서버 내부 오류가 발생했습니다. | 전체(예상하지 못한 오류), 13 · 19 · 20 · 21(데이터 정합성 오류) |


## 관리자 마트 거절 환불 재처리

- `POST /api/admin/orders/{orderId}/refunds/{refundId}/retry`
- 권한: SUPER_ADMIN, 해당 주문 마트의 MART_ADMIN. 본문 없음. `Idempotency-Key` 필수(최대 64자, 공백 불가, 소문자로 정규화).
- 대상: 마트 거절(`CANCEL_REQUESTED`, `REJECTED`, `MART_REJECTED`) 주문의 가장 최근 `FAILED`·`ADMIN` 환불. 상품 하자·배송완료 주문의 임의 환불 API가 아니다.
- 같은 키는 같은 주문·원본 환불에 대한 기존 결과를 반환한다. 다른 대상에 키를 재사용하면 COMMON-011. 원본 환불을 갱신하지 않고 새 ADMIN 환불을 생성한다. 원본 식별은 새 행의 서버 생성 사유(`마트 거절 환불 재처리 #원본ID`)에 남긴다.
- 접수: 첫 DB 작업으로 PAYMENT 잠금 → 마트 권한 확인 → 같은 키 확인 → ORDER 잠금·거절 상태 확인 → 최신 실패 확인 → PAYMENT를 CANCEL_REQUESTED로 변경 + 새 REFUND + REFUND_RETRY 감사 → COMMIT.
- 외부 PG 처리는 기존 AdminRefundExecutionFacade를 재사용하고 트랜잭션 밖에서 실행한다. 성공 시 주문 종료·재고·포인트 복원은 기존 완료 경로에서 한 번만 수행한다.
- 완료 200, 처리 중 202. 응답은 마트 거절 API의 MartRejectionResponse와 동일하다. 환불 상태 부적합 PAYMENT-007(409), 다른 키 처리 중 COMMON-006(409), 주문 상태 부적합 ORDER-015(409), 마트 권한 AUTH-005(403). 실제 enum 코드가 우선이다.
