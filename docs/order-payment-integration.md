# 주문 ↔ 결제 연동 기준

최종 확정본 · 레시픽(Recipe-pick) · 주문 도메인 ↔ 결제 도메인 · 확정일: 2026-10-15 · 외부 취소(10절) 추가: 2026-10-26

> 핵심: 주문·결제·재고의 강한 정합성이 필요한 처리는 Facade가 같은 DB 트랜잭션에서 동기 호출합니다.

## 1. 기본 원칙

1. 주문 생성과 결제 생성은 같은 DB 트랜잭션에서 처리한다.

2. 한 주문과 한 결제는 1 : 1 관계를 유지한다.

3. 재결제할 때 기존 주문과 결제를 되살리거나 재사용하지 않는다.

4. 이미 결제 대기 주문이 있으면 새 주문 생성을 거절한다.

5. 사용자는 기존 결제를 계속 진행하거나 기존 대기 주문을 명시적으로 취소해야 한다.

6. 기존 주문이 결제·취소·5분 만료로 종료된 후에만 새 ORDER와 PAYMENT를 생성한다.

7. 새 주문 요청이 들어왔다고 기존 대기 주문을 자동 종료하지 않는다.

8. 따라서 종료 사유 REPLACED_BY_NEW_ORDER는 사용하지 않는다.

9. 주문 생성 시 예약재고를 별도로 두지 않고 실제 재고를 즉시 차감한다.

10. 결제 성공 시 재고를 추가로 변경하지 않는다.

11. 주문이 실패·취소·마트 거절로 최종 종료될 때 최초 차감 재고를 정확히 한 번 복구한다.

12. 결제 승인·웹훅·5분 만료는 결제 도메인이 상태 확정의 주 진입점이 된다.

13. 여러 도메인을 함께 변경하는 흐름은 Facade가 결제 쪽 서비스와 주문 쪽 OrderService·OrderFacade를 조립한다.

14. 주문 서비스와 결제 서비스는 서로 직접 주입하지 않는다.

15. PortOne 외부 호출은 항상 DB 트랜잭션 밖에서 수행한다.

16. 외부 호출 전후의 내부 상태 반영은 각각 짧은 DB 트랜잭션으로 처리한다.

17. 호출받는 도메인 서비스는 기존 트랜잭션에 합류하고 REQUIRES_NEW를 사용하지 않는다.

18. 결제와 주문을 함께 잠글 때는 결제 → 주문 순서로 비관적 락을 획득한다.

19. 2차 포인트까지 포함하면 락 순서는 결제 → 주문 → MEMBER_POINT → POINT_EARNING으로 고정한다.

20. 같은 API 요청·웹훅·큐 메시지가 반복돼도 재고와 상태 변경은 한 번만 반영한다.

## 2. 신규 주문 생성

```text
신규 주문 생성 요청
↓
기존 PENDING_PAYMENT 주문 확인
```

### 기존 대기 주문이 있는 경우

```text
새 주문 생성 거절
↓
기존 결제를 계속 진행하거나
기존 대기 주문을 명시적으로 취소하도록 안내
```

기존 주문을 자동으로 끝내지 않는다.

### 기존 대기 주문이 없는 경우

하나의 트랜잭션에서 다음을 처리한다.

```text
ORDER = PENDING_PAYMENT
ORDER.pending_member_id = 회원 ID

PAYMENT = IN_PROGRESS

재고
→ 주문 수량만큼 즉시 차감

POINT_EARNING
→ PENDING
(2차 포인트 적용 시)
```

pending_member_id는 회원당 결제 대기 주문 1건을 강제한다.

## 3. 결제 성공

PortOne 조회·결제금액 검증은 DB 트랜잭션 밖에서 수행한다.

검증 성공 후 결제 → 주문 순서로 잠그고 같은 트랜잭션에서 다음을 반영한다.

```text
PAYMENT
IN_PROGRESS → PAID

ORDER
PENDING_PAYMENT → PAID

martStatus
NONE → RECEIVED

pending_member_id
회원 ID → null
```

재고는 이미 주문 생성 시 차감했으므로 변경하지 않는다.

markPaid()가 중복 호출되더라도 이미 PAID인 주문이면 재고·이력 등을 다시 처리하지 않는다.

## 4. 결제 전 실패·취소·만료·금액 불일치

다음 사건은 같은 종료 흐름을 사용한다.

결제 명확한 실패

결제 전 회원 취소

5분 결제 만료

서버 주문금액과 PG 결제금액 불일치

같은 트랜잭션에서 다음을 처리한다.

```text
PAYMENT
→ FAILED

ORDER
→ CANCELED

pending_member_id
→ null

재고
→ 주문 생성 시 차감했던 수량 복구

POINT_EARNING
→ CANCELED
```

실패 사유는 각각 구분한다.

| 사건 | Payment failureReason | Order cancelReasonCode |
|---|---|---|
| 결제 전 회원 취소 | CANCELED_BEFORE_PAY | MEMBER_CANCELED |
| 5분 만료 | EXPIRED | PAYMENT_TIMEOUT |
| 결제 명확한 실패 | PG 실패 사유 | PAYMENT_FAILED |
| 금액 불일치 | AMOUNT_MISMATCH | AMOUNT_MISMATCH |

재고 복구와 pending_member_id 해제는 멱등하게 처리한다.

## 5. 만료 후 늦은 결제 승인

5분 만료 시 이미 다음 처리가 끝난 상태다.

```text
PAYMENT = FAILED(EXPIRED)
ORDER = CANCELED
재고 복구 완료
pending_member_id = null
```

그 후 PortOne에서 늦은 결제 승인이 확인되면 다음과 같이 처리한다.

```text
ORDER를 PAID로 되살리지 않음
재고를 다시 변경하지 않음
Payment는 FAILED 유지

↓
결제 도메인이 시스템 환불만 처리
```

이 경우 정상 회원 취소용 markRefunded(orderId)는 호출하지 않는다.

이유는 주문 종료와 재고 복구가 이미 완료됐기 때문이다. 다시 호출하면 재고가 두 번 복구될 수 있다.

## 6. 결제 완료 후 회원 취소 시작

회원 취소 가능 조건은 주문 도메인이 판단한다.

```text
orderStatus == PAID
&& martStatus == RECEIVED
```

일 때만 true를 반환한다.

결제 도메인이 martStatus Enum을 직접 비교하지 않고 order.isCancelableByMember() 또는 주문 서비스의 판단 메서드에 묻는다.

결제 → 주문 순서로 잠근 뒤 같은 트랜잭션에서 다음을 저장한다.

```text
PAYMENT
PAID → CANCEL_REQUESTED

ORDER
PAID → CANCEL_REQUESTED

REFUND
→ REQUESTED

취소 사유
→ MEMBER_CANCELED
```

이 단계에서는 재고를 복구하지 않는다.

트랜잭션을 커밋한 뒤 PortOne 취소 API를 호출한다.

## 7. 회원 취소 결과

### 7-1. PG 취소 성공

같은 트랜잭션에서 다음을 처리한다.

```text
PAYMENT
CANCEL_REQUESTED → CANCELED

REFUND
REQUESTED → COMPLETED

ORDER
CANCEL_REQUESTED → REFUNDED

재고
→ 최초 차감 수량 복구
```

재고 복구는 한 번만 수행한다.

### 7-2. PG 취소 결과 불명

timeout·네트워크 오류 등으로 결과를 확정할 수 없으면 다음 상태를 유지한다.

```text
PAYMENT = CANCEL_REQUESTED 유지
REFUND = REQUESTED 유지
ORDER = CANCEL_REQUESTED 유지
martStatus = RECEIVED 유지
재고 복구 X
```

웹훅 또는 PortOne 재조회로 결과를 확인한다.

### 7-3. PG 취소 명확한 실패

회원이 요청한 취소라면 정상 흐름으로 되돌린다.

```text
PAYMENT
CANCEL_REQUESTED → PAID

REFUND
REQUESTED → FAILED

ORDER
CANCEL_REQUESTED → PAID

martStatus
RECEIVED 유지

재고 복구 X
```

주문은 다시 정상 처리 가능한 상태가 된다.

## 8. 마트 거절 시작

마트 거절은 주문을 즉시 종료하고 재고를 바로 복구하지 않는다.

거절 가능 조건은 주문 도메인이 판단한다. 권장 조건은 다음과 같다.

```text
orderStatus == PAID
&& martStatus == RECEIVED
```

결제 → 주문 순서로 잠근 뒤 같은 트랜잭션에서 다음 상태로 변경한다.

```text
PAYMENT
PAID → CANCEL_REQUESTED

ORDER
PAID → CANCEL_REQUESTED

martStatus
RECEIVED → REJECTED

REFUND
→ REQUESTED

취소 사유
→ MART_REJECTED
```

이 단계에서는 재고를 복구하지 않는다.

트랜잭션 커밋 후 PortOne 취소 API를 호출한다.

TX1 순서(2026-10-28 확정): 같은 Idempotency-Key replay 확인 → PAYMENT 잠금(orderId) → requestMartRejection → PAYMENT requestCancel → REFUND(requested_by = ADMIN) 생성 → ORDER_REJECT 감사 기록(새 실행만, 거절 사유 원문 제외) → COMMIT. 결제 전 주문(ORDER-012)·다른 마트 주문(AUTH-005)은 예외로 TX1 전체를 롤백하고, 상태 판정(NOT_REJECTABLE·ALREADY_REJECTED)은 반환값이라 결제 Facade가 응답 코드로 바꾼다.

PG 취소 명확한 실패(9-3)에서 ADMIN 환불은 restorePaidAfterMemberCancelFailure()를 부르지 않는다. 주문 사유가 MART_REJECTED면 그 메서드는 INVALID_ORDER_STATUS_TRANSITION을 던진다.

## 9. 마트 거절 환불 결과

### 9-1. PG 취소 성공

```text
PAYMENT
CANCEL_REQUESTED → CANCELED

REFUND
REQUESTED → COMPLETED

ORDER
CANCEL_REQUESTED → REFUNDED

martStatus
REJECTED 유지

재고
→ 최초 차감 수량 복구
```

회원 취소와 마트 거절 모두 최종 성공 처리는 markRefunded(orderId)로 통일한다.

### 9-2. PG 취소 결과 불명

```text
PAYMENT = CANCEL_REQUESTED 유지
REFUND = REQUESTED 유지
ORDER = CANCEL_REQUESTED 유지
martStatus = REJECTED 유지
재고 복구 X
```

웹훅·재조회로 후속 확인한다.

### 9-3. PG 취소 명확한 실패

마트가 이미 주문을 처리할 수 없다고 거절했으므로 PAID + RECEIVED로 정상 복귀시키지 않는다.

금전적으로는 PG 취소가 실패해 실제 결제가 유지되므로 다음과 같이 처리한다.

```text
PAYMENT
CANCEL_REQUESTED → PAID

REFUND
REQUESTED → FAILED

ORDER
CANCEL_REQUESTED 유지

martStatus
REJECTED 유지

재고 복구 X
```

이 건은 관리자 재처리 대상으로 둔다.

재시도할 때는 다음과 같이 새로운 실행 기록으로 처리한다.

```text
새 REFUND 행
새 Idempotency-Key
PAYMENT PAID → CANCEL_REQUESTED
```

restorePaidAfterCancelFailure()는 회원 직접 취소 실패에만 사용한다.

## 10. 외부 취소

추가일: 2026-10-26

PortOne 관리자 콘솔처럼 우리 시스템 밖에서 결제가 전액 취소된 경우다. 실제 결제는 이미 취소됐는데 회원·시스템이 시작한 환불 행이 없어 DB에는 PAYMENT·ORDER가 PAID로 남는다.

결제 도메인이 웹훅 또는 PortOne 재조회로 감지하고 상태 확정의 주 진입점이 된다. 판정 기준은 다음과 같고, 세부 판정은 결제 도메인이 정한다.

```text
PortOne 결제 상태 = CANCELLED(전액)
PAYMENT = PAID 또는 CANCEL_REQUESTED
결제 도메인이 시작한 취소로 설명되지 않는 PortOne 취소 ID
```

PortOne 호출이 없으므로 트랜잭션 하나로 끝난다. 결제 → 주문 → (2차: MEMBER_POINT → POINT_EARNING) → 재고 순서로 잠근다.

### 10-1. 주문이 CANCEL_REQUESTED인 경우

회원 취소·마트 거절의 PG 취소 결과가 불명한 사이에 콘솔에서 취소됐거나, PG 취소가 명확히 실패한 뒤 운영자가 콘솔로 재처리한 경우다. 새 정책이 아니라 7-1·9-1의 PG 취소 성공과 같이 처리한다.

```text
PAYMENT
CANCEL_REQUESTED 또는 PAID → CANCELED

REFUND
REQUESTED 행이 있으면 → COMPLETED
FAILED 뒤라면 → requested_by = EXTERNAL 행 추가(COMPLETED)

ORDER
CANCEL_REQUESTED → REFUNDED

취소 사유
→ 최초 사유(MEMBER_CANCELED 또는 MART_REJECTED) 유지

재고
→ 최초 차감 수량 복구
```

주문은 markRefunded(orderId)로 끝낸다. 환불 행을 어떻게 남길지는 결제 도메인이 정한다.

### 10-2. 주문이 PAID인 경우

취소 요청이 없는데 PG에서 먼저 취소된 경우다. 같은 트랜잭션에서 다음을 처리한다.

```text
PAYMENT
PAID → CANCELED

REFUND
→ COMPLETED
(requested_by = EXTERNAL, PortOne 취소 ID·취소 시각 저장)

ORDER
PAID → REFUNDED

취소 사유
→ EXTERNAL_CANCELED

martStatus
변경 없음

재고
→ martStatus가 RECEIVED·PREPARING이면 최초 차감 수량 복구
→ SHIPPING·DELIVERED면 복구하지 않고 WARN, 관리자 재고 조정 대상
```

CANCEL_REQUESTED를 거치지 않는다. CANCEL_REQUESTED는 PG 결과를 기다리는 상태인데 외부 취소는 결과가 이미 확정돼 기다릴 것이 없다. 그 상태에 두면 미해결 환불로 남아 회원 탈퇴를 막고, 관리자 재처리가 이미 취소된 결제를 다시 취소하러 간다.

CANCELED가 아니라 REFUNDED다. CANCELED는 결제 전 종료이고, 돈이 나갔다 돌아온 주문은 REFUNDED다.

martStatus는 바꾸지 않는다. REJECTED는 마트가 거절했다는 뜻이다. 마트 수락과 배송 상태 변경은 모두 orderStatus == PAID를 전제하므로 REFUNDED가 되면 별도 장치 없이 막힌다.

재고는 마트가 상품을 내보내기 전에만 복구한다. 기존 REFUNDED 경로는 모두 RECEIVED에서 출발해 "종료 시 1회 복구"가 늘 맞았지만, 외부 취소는 배송 중·배송 완료 주문에도 올 수 있다.

2차 포인트는 기존과 같이 주문 종료와 재고 복구 사이에서 POINT_EARNING을 CANCELED로 바꾼다. 배송 완료 후 적립이 확정된 포인트는 회수하지 않는다.

주문 상태 이력은 actor SYSTEM, PAID → REFUNDED 한 줄로 남긴다.

### 10-3. 부분 취소

부분 환불은 정책상 없다. 콘솔에서 일부 금액만 취소되더라도(PARTIAL_CANCELLED) 주문·결제 상태를 바꾸지 않는다. 환불 금액 기록을 남길지는 결제 도메인이 정한다.

### 10-4. 멱등

webhook_id, portone_cancellation_id UNIQUE로 같은 사건을 한 번만 반영한다. 주문이 이미 REFUNDED면 재고를 다시 복구하지 않는다.

## 11. 재고 복구 기준 최종표

| 상황 | 재고 복구 |
|---|---|
| 주문 생성 | 실제 재고 즉시 차감 |
| 결제 성공 | 변경 없음 |
| 결제 전 회원 취소 | 복구 |
| 5분 만료 | 복구 |
| 결제 명확한 실패 | 복구 |
| 금액 불일치 | 복구 |
| 회원 취소 요청 시작 | 복구 안 함 |
| 회원 취소 성공 | 복구 |
| 회원 취소 결과 불명 | 복구 안 함 |
| 회원 취소 명확한 실패 | 복구 안 함 |
| 마트 거절 접수 | 복구 안 함 |
| 마트 거절 환불 성공 | 복구 |
| 마트 거절 결과 불명 | 복구 안 함 |
| 마트 거절 환불 명확한 실패 | 복구 안 함 |
| 만료 후 늦은 승인 자동환불 | 이미 복구했으므로 추가 복구 안 함 |
| 외부 취소, 주문 CANCEL_REQUESTED | 복구(회원 취소·마트 거절 환불 성공과 동일) |
| 외부 취소, 주문 PAID, 마트 RECEIVED·PREPARING | 복구 |
| 외부 취소, 주문 PAID, 마트 SHIPPING·DELIVERED | 복구 안 함(관리자 재고 조정) |
| 부분 취소 | 변경 없음 |

## 12. 주문 쪽 메서드 계약 — 최종

| 메서드 | 주문 도메인의 역할 | 호출 시점 |
|---|---|---|
| OrderService.markPaid(orderId, paidAt) | 주문 PAID, 마트 RECEIVED, 결제 시각 저장, pending_member_id 해제, 이력 2건(ORDER·MART). 재고 변경 없음. 결제 완료 장바구니 정리용 `OrderPaidEvent`를 발행하고 정리는 커밋 뒤 리스너가 처리. 이미 PAID면 아무것도 하지 않음(멱등) | 결제 확정 API·결제 완료 웹훅·만료 재조회, 0원 결제(주문 생성 트랜잭션) |
| OrderFacade.closeByPaymentFailure(orderId, reasonCode, reasonDetail) | MANDATORY. 주문 CANCELED, pending 해제, 사유·이력 저장 뒤 복구할 상품(`StockQuantity`)을 반환. 재고는 직접 복구하지 않음. 이미 CANCELED면 빈 목록(멱등, 최초 사유 유지). 이력 주체는 MEMBER_CANCELED면 MEMBER, 그 밖은 SYSTEM | 결제 전 취소·5분 만료·결제 실패·금액 불일치 |
| OrderService.markMemberCancelRequested(orderId, reasonDetail) | 주문 PAID·마트 RECEIVED면 CANCEL_REQUESTED로 바꾸고 MEMBER_CANCELED 사유를 저장한 뒤 REQUESTED 반환. PAID가 아니면 INVALID_ORDER_STATUS, 마트가 준비를 시작했으면 MART_ALREADY_PREPARING을 반환하고 상태를 바꾸지 않음. 주문은 같은 요청인지 알 수 없으므로 반복 요청은 결제 Idempotency-Key가 먼저 거름. 재고 변경 없음. 응답 코드는 결제 쪽이 정한다 | 회원 취소 시작 |
| OrderService.requestMartRejection(role, adminMartId, adminId, orderId, reasonDetail) | MANDATORY(결제 TX1 안에서만 호출, 단독 호출 거부). 주문 행 FOR UPDATE → 마트 NONE이면 ORDER-012, MART_ADMIN 소속 검사 AUTH-005(둘은 예외로 TX1 롤백) → 마트 REJECTED면 ALREADY_REJECTED, PAID+RECEIVED 아니면 NOT_REJECTABLE 반환 → 주문 PAID → CANCEL_REQUESTED, 마트 RECEIVED → REJECTED, MART_REJECTED 사유 저장, 이력 2건(ORDER·MART, actor MART, changedByAdminId), REJECTED 반환. 재고 변경 없음. 응답 코드는 결제 쪽이 정한다. 같은 키 재전송은 결제 Idempotency-Key가 먼저 거른다 | 마트 거절 접수 |
| OrderService.validateMartRefundRetry(role, adminMartId, orderId) | MANDATORY. 주문 행 FOR UPDATE → MART_ADMIN 소속 검사 AUTH-005 → 주문 CANCEL_REQUESTED·마트 REJECTED·사유 MART_REJECTED가 아니면 ORDER-015. 상태를 바꾸지 않음 | 마트 거절 환불 재처리 접수 |
| OrderService.restorePaidAfterMemberCancelFailure(orderId) | 주문 CANCEL_REQUESTED → PAID, 취소 사유 비움, 마트 RECEIVED 유지. 재고 변경 없음. 이미 PAID면 아무것도 하지 않음(멱등) | 회원 취소가 명확히 실패 |
| OrderFacade.markRefunded(orderId) | MANDATORY. 주문 CANCEL_REQUESTED → REFUNDED, 이력 저장 뒤 복구할 상품을 반환. 이미 REFUNDED면 빈 목록(멱등) | 회원 취소 또는 마트 거절의 PG 환불 성공(외부 취소로 확인된 경우 포함) |
| OrderFacade.closeByExternalCancel(orderId, reasonDetail) | MANDATORY. 주문 PAID → REFUNDED, EXTERNAL_CANCELED 사유·이력 저장. 마트 RECEIVED·PREPARING일 때만 복구할 상품을 반환하고, 배송이 시작됐으면 빈 목록과 WARN 로그. 마트 상태 변경 없음. 이미 REFUNDED면 빈 목록(멱등) | 외부 취소 감지(주문이 PAID) |
| OrderFacade.restoreStock(items) | MANDATORY. closeByPaymentFailure·markRefunded·closeByExternalCancel이 돌려준 상품의 재고를 복구. 빈 목록이면 아무것도 하지 않음. 잠금 순서(주문 → 포인트 → 재고)를 지키려고 주문 종료와 나눔 | 결제 Facade가 주문 종료 → 포인트 복구 뒤에 호출 |
| OrderService.getMemberIdOf(orderId)·getMartIdOf(orderId) | 주문의 회원·마트 ID만 값으로 조회. 주문 엔티티를 영속성 컨텍스트에 올리지 않아 같은 트랜잭션의 뒤이은 주문 잠금 조회가 최신 상태를 읽음 | 결제 Facade가 주문을 잠그기 전의 소유자·마트 확인 |
| Order.isCancelableByMember() | orderStatus == PAID && martStatus == RECEIVED일 때만 true | 회원 취소 판단 |
| Order.isRejectableByMart() | orderStatus == PAID && martStatus == RECEIVED일 때만 true | 마트 거절 판단 |

위 표는 2026-11-06 코드 기준으로 갱신했다(#543). 처음 확정안과 달라진 점은 세 가지다.

- 주문 종료 메서드는 재고를 직접 복구하지 않고 복구할 상품만 돌려준다. 결제 Facade가 같은 트랜잭션에서 주문 종료 → 포인트 복구 → `restoreStock` 순으로 호출한다. 2차 포인트가 들어오며 잠금 순서(주문 → MEMBER_POINT → POINT_EARNING → 재고)에서 포인트 복구가 주문과 재고 사이에 놓이게 됐기 때문이다. 재고 복구 시점(11절)은 바뀌지 않았다.
- 주문 행 잠금(`findByIdForUpdate`)은 위 메서드들이 내부에서 수행한다. 결제 쪽이 주문 리포지토리를 직접 부르지 않는다.
- 마트 거절 환불 재처리(`validateMartRefundRetry`)와 잠금 전 값 조회(`getMemberIdOf`·`getMartIdOf`)가 추가됐다.

closeByExternalCancel()을 위해 주문 상태 전이에 PAID → REFUNDED를 추가한다. 이 전이는 외부 취소에만 쓴다.

markMartRejectedAndCancelRequested()는 2026-10-28에 requestMartRejection()으로 이름을 바꿨다. 회원 취소(markMemberCancelRequested)와 같이 OrderService에 두고 상태 판정을 반환값으로 돌려준다.

다음 메서드는 최종안에서 제거한다.

> closeByMartRejection()

마트 거절은 즉시 종료가 아니라 CANCEL_REQUESTED 진입 후 PG 환불 성공 시 markRefunded()로 끝낸다.

## 13. 주문 취소 사유 코드 — 최종

주문에는 다음 값을 저장한다.

```text
cancel_reason_code
cancel_reason_detail
```

| 코드 | 의미 |
|---|---|
| MEMBER_CANCELED | 회원 직접 취소 |
| PAYMENT_TIMEOUT | 결제 대기 5분 만료 |
| PAYMENT_FAILED | 결제 실패 확정 |
| AMOUNT_MISMATCH | 결제 금액 불일치 |
| MART_REJECTED | 마트 주문 거절 |
| EXTERNAL_CANCELED | PortOne 콘솔 등 시스템 밖에서 일어난 전액 취소 |

다음 코드는 제거한다.

> REPLACED_BY_NEW_ORDER

신규 주문 요청 때문에 기존 대기 주문을 자동 종료하지 않기 때문이다.

중복 알림이 들어와도 최초 취소 사유를 덮어쓰지 않는다.

## 14. Facade와 트랜잭션 최종 기준

### 주문 생성

```text
Order 생성 진입점
↓
Facade 트랜잭션

Order 생성
Payment 생성
재고 차감
pending_member_id 설정
↓
같이 커밋
```

### 결제 확정·실패·만료

```text
PaymentFacade 트랜잭션

PaymentCommandService
+
OrderCommandService
↓
같이 커밋
```

### PG 취소

```text
트랜잭션 1
CANCEL_REQUESTED 저장
↓
커밋

PortOne 호출
↓
트랜잭션 밖

트랜잭션 2
성공·실패 결과 반영
주문 상태 변경
필요 시 재고 복구
↓
같이 커밋
```

### 외부 취소

```text
웹훅 또는 PortOne 재조회
↓
트랜잭션 하나

PAYMENT CANCELED
EXTERNAL 환불 행
주문 REFUNDED
필요 시 재고 복구
↓
같이 커밋
```

> ApplicationEvent는 주문·결제·재고의 핵심 상태 변경에 사용하지 않습니다. 알림·통계·로그처럼 일시적 불일치를 허용할 수 있는 후처리에만 사용합니다.

## 15. 최종 판정

준희님이 보낸 4개 제안은 다음과 같이 모두 반영한다.

| 준희님 제안 | 최종 판정 |
|---|---|
| 기존 대기 주문이 있으면 신규 주문 거절 | 수용 |
| 마트 거절도 PG 취소 성공 후 최종 종료·재고 복구 | 수용 |
| PG 취소 실패를 회원 취소와 마트 거절로 구분 | 수용 |
| 만료 후 늦은 승인 환불에서는 주문·재고를 다시 건드리지 않음 | 수용 |

정책상 추가로 정할 큰 항목은 없다.

외부 취소(10절)는 2026-10-26에 추가해 확정한 항목이다. 추가 당시 미확정으로 두었던 항목과 판정은 다음과 같다.

| 추가 당시 미확정 항목 | 최종 판정 |
|---|---|
| PREPARING에서 재고 복구 여부 | 복구로 확정. 포장 중이라 상품이 마트를 떠나지 않았고, 마트 거절의 RECEIVED 복구와 같은 논리다. 마트가 출고 뒤 상태를 늦게 바꿔 과다 복구된 경우는 관리자 재고 조정으로 잡는다 |
| 부분 취소 기록 | 10-3으로 정리. 주문·결제 상태는 바꾸지 않고, 환불 금액 기록을 남길지는 결제 도메인이 정한다 |

다만 현재 팀 컨벤션 문서에는 핵심 상태 변경의 반대 방향 알림을 ApplicationEvent로 처리하는 문구가 남아 있으므로, Facade + 동일 트랜잭션 동기 호출로 교체해야 한다.

또한 컨벤션은 현재 주문·마트 상태 변경을 조건부 UPDATE로 규정하고 있는데, 이번 최종 계약은 비관적 락과 고정된 락 순서를 사용하므로 이 부분도 하나로 맞춰야 한다. 이번 연동 기준을 최종으로 따른다면 컨벤션에 “주문↔결제의 다중 도메인 상태 전이는 비관적 락을 사용한다”는 예외 또는 최종 원칙을 반영해야 한다.

> 최종 핵심: 기존 대기 주문은 자동 종료하지 않고 새 주문을 거절합니다. 회원 취소와 마트 거절은 PG 취소 성공 후에만 주문을 REFUNDED로 종료하고 재고를 복구합니다.
