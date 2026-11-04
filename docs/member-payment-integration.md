# 회원·멤버십 ↔ 결제 연동 기준

레시픽(Recipe-pick) · 회원·인증·멤버십 도메인 ↔ 결제 도메인 · 기준: `dev` `9172f6d` (2026-11-04, 3차 구현 완료)

> 핵심: 이용권의 상태는 회원 도메인이, 돈의 흐름(빌링키·청구·환불)은 결제 도메인이 소유합니다. 두 도메인을 함께 바꾸는 처리는 회원 잠금으로 시작하는 한 트랜잭션에서 회원 쪽 `MANDATORY` 계약을 동기 호출하고, PortOne 호출은 언제나 트랜잭션 밖(`NEVER` 진입점)에서 합니다.

이 문서는 두 도메인 사이의 계약과 호출 순서만 모은 통합 안내다. 주문 쪽 짝은 [주문 ↔ 결제 연동 기준](order-payment-integration.md)이고, 세부 정책·분기·오류 응답은 아래 문서가 기준이다.

| 주제 | 상세 문서 |
|---|---|
| 카드 등록·교체 | [billing-key-registration](api/billing-key-registration.md) |
| 카드 원격 삭제·탈퇴 정리 | [billing-key-cleanup](api/billing-key-cleanup.md) |
| 바로 구독 API | [membership-subscribe](api/membership-subscribe.md) |
| 체험 시작 API | [membership-trial-start](api/membership-trial-start.md) |
| 첫 결제(구독·체험 전환) | [membership-first-charge](api/membership-first-charge.md) |
| 월 갱신·재시도·유예 | [membership-renewal](api/membership-renewal.md) |
| 결과 불명·복구·웹훅·운영 재조회 | [membership-reconciliation](api/membership-reconciliation.md) |
| 즉시 해지 API | [membership-terminate](api/membership-terminate.md) |
| 이용료 환불·SYSTEM 환불 | [membership-refund](api/membership-refund.md) |
| 미정산 판정 C-17 | [membership-payment-unresolved](api/membership-payment-unresolved.md) |
| 회원 탈퇴 | [member-withdrawal](api/member-withdrawal.md) |

## 1. 기본 원칙

1. 회원 도메인은 `MemberService`·`MembershipBillingService`의 공개 메서드와 DTO(`BillingMembershipData`, `MembershipConsent`, `MembershipBillingTarget`, `MembershipBillingResult`, `MemberSettlementLock`, `MemberProfile`)만 결제에 내보낸다. 엔티티는 넘기지 않는다.

2. 결제 도메인은 회원 쪽에 `MembershipSubscriptionFacade`(C-11), `MembershipRefundFacade`(C-5), `BillingKeyService`(C-10), `MembershipPaymentQueryService`(C-17)와 탈퇴 연결 구현(`WithdrawalSettlementChecker` 4개, `WithdrawalPaymentMethodPreparer` 1개)만 내보낸다.

3. 결제의 멤버십 테이블에는 `member_id`가 없다. 결제 → 회원 매핑은 잠금 트랜잭션에 들어가기 전에 `findMemberIdOfMembership(membershipId)`로만 한다.

4. 두 도메인을 바꾸는 트랜잭션의 첫 DB 작업은 회원 행 잠금이다. 잠금 전 일반 조회는 하지 않는다.

5. 신규 거래(구독 접수, 체험 전환 접수, 갱신 접수, 카드 등록, 즉시 해지 접수)는 `lockActiveMember`로 시작해 탈퇴 회원을 `MEMBER-001`로 거절한다.

6. 이미 시작된 거래의 정산·복구(청구 결과 반영, 환불 결과 반영, 회차 정리, SYSTEM 환불 재실행, 키 폐기·삭제)는 `lockMemberForSettlement`로 시작하고 탈퇴 회원도 잠근다. 탈퇴가 이미 시작된 거래의 후속 처리를 막지 않는다.

7. 잠금 순서는 MEMBER → 이용권(C-7) → 회차 PK → 시도 PK 오름차순 → 환불 PK 오름차순이다. 빌링키 삭제는 MEMBER → BILLING_KEY → BILLING_KEY_DELETION이다.

8. 회원 쪽 변경 계약은 모두 `Propagation.MANDATORY`이고, 읽기 전용 바깥 트랜잭션이면 `IllegalStateException`으로 거절한다.

9. 회원 쪽 반영 계약은 이미 반영됨·상태 충돌을 예외가 아니라 `MembershipBillingResult`(`ALREADY_APPLIED`, `CONFLICT`)로 돌려준다. 예외가 나면 같은 트랜잭션의 결제 기록도 롤백된다.

10. 회원 쪽 계약은 현재 시각을 만들지 않는다. 호출자가 모든 잠금을 잡은 뒤 읽은 시각을 넘긴다.

11. PortOne 호출은 트랜잭션 밖에서 한다. PG까지 가는 진입점은 회원·결제 모두 `Propagation.NEVER`로 바깥 트랜잭션 안의 호출을 거절한다.

12. 흐름은 `접수 TX(회원 잠금 → 상태 저장) → 커밋 → PortOne → 결과 반영 TX(회원 정산 잠금 → 반영)`로 나눈다. 최종 결과는 POST 응답이 아니라 같은 PortOne ID의 GET으로만 확정한다.

13. 두 도메인 사이 경로에서 `REQUIRES_NEW`는 쓰지 않는다. (회원 도메인 안 `DuplicateMemberChecker`의 가입 중복 확인만 `REQUIRES_NEW` 읽기를 쓰며 이 연동과 무관하다.)

14. 결과 불명은 실패가 아니다. 미해결로 남기고 웹훅·복구·운영 재조회로 확정한다.

15. 미해결·미정산 멤버십 결제가 하나라도 있으면(C-17) 구독·체험 시작·관리자 지급·전환·갱신 접수·즉시 해지·탈퇴를 막는다.

16. 회원당 ACTIVE 빌링키는 1개다. 키 상태 변경이 모두 회원 잠금 아래에서 일어나는 것으로 보장하며, 청구 경로는 키 행을 잠그지 않는다.

17. 즉시 해지는 MEMBER 환불이 COMPLETED일 때만 이용권을 종료하고, 그 종료는 환불 완료와 같은 트랜잭션에서 한다. SYSTEM 환불 완료는 이용권을 바꾸지 않는다.

18. 신규 청구 스위치(`recipick.membership-billing.enabled`, 운영 기본 false)는 새 접수·새 POST만 막는다. 재조회·결과 반영·복구·환불은 스위치와 무관하게 돈다.

## 2. 패키지 경계

### 결제 → 회원

| 회원 쪽 계약 | 결제의 사용처 |
|---|---|
| `MemberService.lockActiveMember` | 신규 거래 TX의 첫 작업 |
| `MemberService.lockMemberForSettlement` | 정산·복구 TX의 첫 작업 |
| `MemberService.getProfile` | 카드 등록 요청 발급의 회원 확인, 청구 시 PortOne customer(이름·이메일·전화) |
| `MembershipBillingService` | 이용권 잠금·조회·생성·결과 반영(11절) |
| `WithdrawalSettlementChecker`, `WithdrawalPaymentMethodPreparer` | 회원이 정의한 인터페이스를 결제가 구현 |

### 회원 → 결제

| 결제 쪽 계약 | 회원의 사용처 |
|---|---|
| `MembershipSubscriptionFacade.subscribe` (C-11) | `MembershipSubscribeFacade` — `POST /api/memberships/subscribe` |
| `MembershipRefundFacade.requestMemberRefund` (C-5) | `MembershipCancelFacade` — `POST /api/memberships/{membershipId}/terminate` |
| `BillingKeyService.hasActiveBillingKey` (C-10) | `MembershipTrialFacade` — 체험 시작 |
| `MembershipPaymentQueryService.existsUnresolved` (C-17) | `MembershipTrialFacade`, `MembershipGrantFacade` |
| Checker 4개, `BillingKeyWithdrawalPreparer` | 탈퇴 Processor가 `ObjectProvider`로 받아 호출 |

적립률은 주문 도메인이 조립한다. `OrderFacade`가 `MembershipService.getTypeAt(memberId, now)`를 한 번 읽어 결제 `PointPolicyService.resolveRate(Optional<MembershipType>)`에 넘긴다(`PAID` 1%, 그 외 0.5%). 결제가 회원을 직접 부르지 않는다.

## 3. 멤버십 결제 흐름

### 3-1. 바로 구독 (C-11)

```text
MembershipSubscribeFacade.subscribe                    회원, NEVER
↓
MembershipSubscriptionFacade.subscribe                 결제, NEVER
↓
TX1  MembershipSubscriptionProcessor.accept
     lockActiveMember
     → 멱등 키 조회("{memberId}:{소문자 키}")       같은 키면 기존 결과
     → 스위치 → existsBlockingMembership → C-17 → ACTIVE 카드 → 동의 3,900
     → createPendingSubscription (C-12a) → C-7
     → MEMBERSHIP_PAYMENT = PENDING, ATTEMPT = READY
     커밋
↓
MembershipChargeExecutor.execute                       NEVER, 트랜잭션 밖
     getProfile → 선점 TX(시도 PK만, READY → DISPATCHING) → PortOne POST → GET
↓
TX2  MembershipChargeResultProcessor                   5절
```

응답은 HTTP 200, `data.status`는 `IN_PROGRESS`·`PAID`·`FAILED`·`UNKNOWN`이다. 멱등 키 조회가 중복 이용권 검사보다 앞이라 재전송이 자기 대기 이용권 때문에 `MEMBERSHIP-002`를 받지 않는다. 상세: [membership-subscribe](api/membership-subscribe.md), [membership-first-charge](api/membership-first-charge.md).

### 3-2. 체험 시작과 체험 → 유료 전환

체험 시작은 결제를 호출만 하고 PG는 없다.

```text
MembershipTrialFacade.start                            회원, @Transactional
getMemberForUpdate → 동의·체험 자격
→ hasActiveBillingKey (C-10)       없으면 MEMBERSHIP-005
→ existsUnresolved(findMembershipIds) (C-17·C-8)       있으면 MEMBERSHIP-009
→ 체험 생성
```

전환은 결제 스케줄러가 시작한다.

```text
TrialConversionFacade.runOnce                          NEVER, 5분, 스위치 ON만
findTrialConversionTargets (C-13)
↓
TX1  TrialConversionProcessor.accept
     lockActiveMember → 체험 C-7 → 전환 대기 재확인 → C-17(있으면 건너뜀)
     → ACTIVE 카드 없음: closeTrialConversion (C-16)  결제 없이 일반 회원
     → 있음: createPendingConversion  체험 autoRenew=false, TRIAL_CONVERT 결제 대기 PAID 생성
            → 새 이용권 C-7 → 회차 PENDING + 시도 READY
     커밋
↓
MembershipChargeExecutor.execute → TX2
```

### 3-3. 월 갱신과 3일 유예

```text
MembershipRenewalFacade.runOnce                        NEVER, 5분, 스위치 ON만
findRenewalTargets (C-2)          활성 PAID·autoRenew·해지 예약 없음·endAt ≤ now < graceEndAt
↓
TX   MembershipRenewalProcessor.accept
     lockActiveMember → C-7 → 이번 기간 회차가 있으면 이어 감
     → 없으면 T ≤ now < G, 동의, 스위치, C-17, existsConflictingMembershipForBilling, ACTIVE 카드
     → 카드 없음: 회차 PAYMENT_METHOD_REQUIRED
     → 카드 있음: 회차 PENDING + 시도 READY
     커밋
↓
MembershipChargeExecutor.execute → TX2
```

T는 현재 `endAt`, G = T + 3일(결제 마감, 회차에 저장)이다. 유예 3일 동안 이용권은 `graceEndAt`까지 유지된다. 확정 실패 뒤 24시간마다 다시 청구하고 회차당 PG 발송은 3회까지다. 최종 실패·마감·해지면 회차 FAILED + `stopRenewal`(C-4b)로 자동갱신만 끄고, 혜택은 회원 도메인의 시각 판정으로 `graceEndAt`에 끝난다. 마감까지 접수가 없던 이용권은 정리 작업(`settleOnce`, `findOverdueRenewalTargets`, `lockMemberForSettlement`)이 FAILED 회차 1건 + C-4b로 닫는다. 상세: [membership-renewal](api/membership-renewal.md).

### 3-4. 카드 등록 뒤 재개

```text
BillingKeyRegistrationFacade.register                  NEVER
PortOne 빌링키 조회·검증(트랜잭션 밖)
↓
TX   BillingKeyRegistrationProcessor.register
     lockActiveMember → BillingKeyService.register(MANDATORY): 같은 키 PK → ACTIVE 키 PK → 등록·교체
     커밋
↓
MembershipRenewalFacade.resumeForMember(memberId)      NEVER, 스위치 ON만
findMembershipIds → PAYMENT_METHOD_REQUIRED 회차마다
TX   resumeExisting: lockActiveMember → C-7 → 같은 회차 재개(새 회차 없음)
→ 실행기
```

재개가 실패해도 카드 등록은 성공으로 응답하고, 갱신 스케줄러가 다시 본다. 상세: [billing-key-registration](api/billing-key-registration.md).

### 3-5. 해지 예약

`POST /api/memberships/{membershipId}/cancel-reservation`은 회원 도메인만으로 끝난다. `MembershipCancelReservationService.reserve`가 회원 → 이용권 PK를 잠그고 `autoRenew = false`, `cancelReservedAt = now`, `graceEndAt = null`을 쓴다. 결제 도메인을 호출하지 않는다.

결제 쪽에는 다음처럼 반영된다.

| 결제 경로 | 해지 예약의 영향 |
|---|---|
| 체험 전환·갱신 후보(C-13·C-2) | `cancelReservedAt IS NULL` 조건으로 빠진다 |
| 접수 TX의 재확인 | 해지 예약이면 `SKIPPED` |
| 예약 전에 접수된 첫 결제·갱신 | 그대로 진행. 성공하면 C-3a·C-4a가 `graceEndAt = null`로 기간만 적용해 다음 갱신부터 중단 |
| 예약 전에 접수된 갱신의 확정 실패 | 재시도 없이 회차 FAILED + C-4b |

## 4. 즉시 해지 환불 (C-5)

```text
MembershipCancelFacade.terminate                       회원, NEVER
↓
MembershipRefundFacade.requestMemberRefund             결제, NEVER
↓
접수 TX  MembershipRefundRequestProcessor.acceptMember
     lockActiveMember → 멱등 키("mrf_member:{memberId}:{소문자 키}")
     → C-7 → 자격 → C-17(MEMBERSHIP-010) → 최신 반영 PAID 회차 PK → 시도 PK → 환불 PK
     → 계산·저장 MEMBERSHIP_REFUND = REQUESTED
     → 0원이면 같은 TX에서 COMPLETED + terminateAfterRefund
     커밋
↓
큐 예약 + MembershipRefundExecutionFacade.execute 1회     NEVER
     실행권 TX(환불 PK만) → GET → 결과 TX2 → (허용 시) 취소 POST → GET → 결과 TX2
↓
결과 TX2  MembershipRefundResultProcessor.apply
     lockMemberForSettlement → C-7 → 회차 → 시도 → 환불
     SUCCEEDED: COMPLETED + (MEMBER면) terminateAfterRefund   같은 TX
     FAILED: FAILED, 이용권 유지
     불명: REQUESTED 유지, 이용권 유지
```

환불액은 `max(0, 결제액 − floor(결제액 × 이용일수 / 기간 일수) − floor(결제액 × 20%))`이며 접수 때 한 번 계산해 저장한다. 불명 건은 1분 주기 환불 복구·취소 웹훅·관리자 재조회가 같은 결과 TX2로 확정하고, 나중에 성공이 확인되면 그때 `terminateAfterRefund`가 이용권을 종료한다.

| 같은 이용권 재요청 | 결과 |
|---|---|
| 같은 키 | 기존 환불의 현재 상태(새 계산·PG 없음), 이용권이 다르면 `COMMON-011` |
| 다른 키, REQUESTED 진행 중 | `MEMBERSHIP-010` |
| 다른 키, COMPLETED 뒤 | `MEMBERSHIP-008` |
| FAILED 뒤 새 키 | 새로 계산해 접수 |

상세(계산 예시·매칭·재POST 창·운영 표시): [membership-refund](api/membership-refund.md), API 응답: [membership-terminate](api/membership-terminate.md).

## 5. 청구 결과 반영 (TX2)

`MembershipChargeResultProcessor`가 첫 결제·갱신 공용이다. `lockMemberForSettlement` → 시도 ID 투영 → C-7 → 회차 PK → 시도 전부 PK 오름차순 → 시각 → 판정 순서이며, 결제 기록과 회원 반영을 같이 커밋한다.

| PG 확정 | 회원 계약 | 결제 쪽 |
|---|---|---|
| 첫 결제 PAID | `applyFirstPaymentPaid` (C-3a) → `ACTIVATED` | 시도·회차 PAID |
| 첫 결제 FAILED | `applyFirstPaymentFailed` (C-3b) → `CLOSED_UNPAID` | 시도·회차 FAILED |
| 갱신 PAID | `applyRenewalPaid(id, E, E + 3일)` (C-4a) → `EXTENDED` | 회차 PAID |
| 갱신 최종 실패 | `stopRenewal` (C-4b) → `RENEWAL_STOPPED` | 회차 FAILED |
| 반영할 수 없는 수납(탈퇴·충돌·금액 불일치·기간 경과) | 대기 이용권이면 C-3b, 끌 수 있으면 C-4b | 회차 FAILED + SYSTEM 환불 REQUESTED |
| 사유 없는 `CONFLICT`·`ALREADY_APPLIED` | 반영 없음 | 전체 롤백 → 별도 TX로 강한 차단 표시 |
| 불명 | 없음 | UNKNOWN 유지, 발송 후 24시간이면 MANUAL_REVIEW |

복구(5분)·검증된 웹훅·운영 재조회는 트랜잭션 밖에서 `findMemberIdOfMembership`으로 회원을 찾고 같은 결제 ID를 재조회해 이 TX2를 탄다. 새 결제 ID로 다시 POST하지 않는다. 상세: [membership-first-charge](api/membership-first-charge.md), [membership-reconciliation](api/membership-reconciliation.md).

## 6. 미정산 판정 (C-17)

`MembershipPaymentQueryService.existsUnresolved(Collection<Long> membershipIds)`는 `MANDATORY`이고 잠그지 않는 일반 조회다. 입력은 종료·만료를 포함한 회원의 모든 이용권 ID(`findMembershipIds`, C-8)다.

| 번호 | 조건 |
|---|---|
| ① | 회차 `PENDING`·`RETRY_WAITING`·`PAYMENT_METHOD_REQUIRED`·`UNKNOWN`·`MANUAL_REVIEW` |
| ② | 시도 `READY`·`DISPATCHING`·`UNKNOWN`·`MANUAL_REVIEW` |
| ③ | 환불 `REQUESTED` |
| ④ | 환불 필요 PAID 시도인데 SYSTEM 환불이 COMPLETED가 아님 |
| ⑤ | 강한 정합성 차단 표시가 있는 시도 |

전제는 호출 TX의 첫 DB 작업이 회원 잠금이고, 이 회원의 회차·시도·환불을 바꾸는 모든 TX가 회원 잠금을 먼저 잡는 것이다. 회원을 잠그지 않는 선점 TX(시도 PK만)와 환불 실행권 TX(환불 PK만)는 미해결 → 미해결로만 바꿔 판정이 변하지 않는다.

| 호출처 | 미정산일 때 |
|---|---|
| 체험 시작, 바로 구독, 관리자 지급 | `MEMBERSHIP-009` |
| 체험 전환·갱신 접수 | 접수 보류(다음 실행) |
| 즉시 해지 접수 | `MEMBERSHIP-010` |
| 탈퇴 준비, `MEMBERSHIP_PAYMENT`·`AUTO_BILLING` 영역 | `BILLING-004` |

갱신 재시도만 `existsUnresolvedForRetry`로 자기 `RETRY_WAITING`·카드 대기 회차 한 행을 제외한다. 상세: [membership-payment-unresolved](api/membership-payment-unresolved.md).

## 7. 회원 탈퇴 정산

```text
MemberWithdrawalFacade.withdraw                        회원, NEVER
↓
[준비 TX — 카드 정리 스위치 둘 다 ON일 때]
MemberWithdrawalPreparationProcessor.prepare
getMemberForUpdate → 비밀번호 → 진행 주문 → 차단 이용권 → 네 Scope 선언 확인
→ BillingKeyWithdrawalPreparer.prepare                MANDATORY
   C-17 → 회원 키 전부(ACTIVE 포함) 삭제 접수(MEMBER → BILLING_KEY → BILLING_KEY_DELETION, PG 없음)
커밋 → PENDING이면 BILLING-030, REVIEW_REQUIRED면 BILLING-031 (접수는 유지)
↓
[최종 TX]
MemberWithdrawalProcessor.withdraw
getMemberForUpdate → 같은 검사 → Checker requireSettled(MANDATORY) @Order 순
→ 장바구니 삭제 → 체험 차단 기록 → 회원 탈퇴 처리
커밋 → Redis 세션 정리
```

| `@Order` | 구현 | Scope | 거절 |
|---|---|---|---|
| 100 | `ProductPaymentSettlementChecker` | `PRODUCT_PAYMENT` | `PAYMENT-012` |
| 200 | `MembershipPaymentSettlementChecker` | `MEMBERSHIP_PAYMENT` | `BILLING-004` |
| 400 | `PointSettlementChecker` | `POINTS`(확인 잔액 비교 후 소멸) | 포인트 영역 코드 |
| 1000 | `AutoBillingWithdrawalSettlementChecker` | `AUTO_BILLING`(C-17 → 운영 확인 → 삭제 미확인 키) | `BILLING-004`·`031`·`030` |

판정만 하는 영역을 앞에, 변경이 있는 포인트를 뒤에 둔다. 어느 Checker가 막아도 같은 탈퇴 트랜잭션 전체가 롤백된다. `AUTO_BILLING`은 `payment.billing-key-deletion.enabled`와 `withdrawal-enabled`가 둘 다 true일 때만 선언되며(운영 기본 false), 네 Scope가 다 선언되지 않으면 탈퇴는 `MEMBER-008`이다. 상세: [member-withdrawal](api/member-withdrawal.md), [billing-key-cleanup](api/billing-key-cleanup.md).

## 8. 빌링키 삭제·폐기·복구의 호출 주체

| 작업 | 시작 | 호출 순서 | 첫 잠금 |
|---|---|---|---|
| 교체·폐기 키 삭제 접수 | `BillingKeyDeletionScheduledTasks.requestReplacedKeys`(5분) | `BillingKeyDeletionRecoveryFacade.requestReplacedOnce`(NEVER) → `BillingKeyDeletionRequestProcessor.requestReplaced` | `lockMemberForSettlement` |
| 탈퇴 키 삭제 접수 | 회원 탈퇴 준비 TX | `BillingKeyWithdrawalPreparer.prepare` → `requestForWithdrawal`(MANDATORY) | 탈퇴 TX의 `getMemberForUpdate` |
| 삭제 실행·재조회 | `recoverDeletions`(1분) | `recoverOnce`(NEVER) → `BillingKeyDeletionExecutionFacade.execute`(NEVER) → `BillingKeyDeletionProcessor` 짧은 TX들 + PortOne GET·DELETE | 각 TX에서 `lockMemberForSettlement` |
| 사용 불가 키 폐기 | 청구 POST가 키 사용 불가 응답 | `BillingKeyReconciliationFacade.verifyUnusable`(NEVER) → `BillingKeyRevocationProcessor.revokeIfConfirmed` | `lockMemberForSettlement` |
| 불명 시도의 키 재확인 | 청구 복구 Sweep | `BillingKeyReconciliationFacade.recheck`(NEVER) → 위와 같음 | `lockMemberForSettlement` |

삭제는 그 키를 쓰는 미해결 시도·강한 차단이 없을 때만 접수하고, DELETE 응답이 아니라 재조회로 `DELETED`를 확인한다. 일반 카드 삭제 API는 없다.

## 9. 트랜잭션 전파와 PG 경계

### 9-1. `Propagation.NEVER` 진입점

| 도메인 | 클래스 | 이유 |
|---|---|---|
| 회원 | `MembershipSubscribeFacade`, `MembershipCancelFacade`, `MemberWithdrawalFacade` | 결제 접수·탈퇴 준비 TX가 독립적으로 커밋돼야 함 |
| 결제 | `MembershipSubscriptionFacade`, `MembershipRefundFacade`, `MembershipChargeExecutor`, `MembershipRefundExecutionFacade` | 커밋 뒤 PortOne 호출 |
| 결제 | `TrialConversionFacade`, `MembershipRenewalFacade`, `MembershipChargeRecoveryFacade`, `MembershipRefundRecoveryFacade` | 스케줄러·복구 진입점 |
| 결제 | `BillingKeyRegistrationFacade`, `BillingKeyReconciliationFacade`, `BillingKeyDeletionRecoveryFacade`, `BillingKeyDeletionExecutionFacade` | 키 조회·삭제 PG 호출 |
| 결제 | `MembershipPaymentAdminFacade`(재조회), `MembershipRefundAdminFacade` | 감사 커밋 뒤 PG 호출 |

(`MemberPasswordFacade`도 `NEVER`이지만 결제와 무관하다.)

### 9-2. `Propagation.MANDATORY` 계약(두 도메인 경계)

| 소유 | 메서드 |
|---|---|
| 회원 `MemberService` | `lockActiveMember`, `lockMemberForSettlement` |
| 회원 `MembershipBillingService` | `getBillingMembershipForUpdate`, `existsConflictingMembershipForBilling`, `createPendingSubscription`, `createPendingConversion`, `applyFirstPaymentPaid`, `applyFirstPaymentFailed`, `applyRenewalPaid`, `stopRenewal`, `closeTrialConversion`, `terminateAfterRefund` |
| 결제 | `MembershipPaymentQueryService.existsUnresolved`·`existsUnresolvedForRetry`, `BillingKeyService.register`, Checker 4개의 `requireSettled`, `BillingKeyWithdrawalPreparer.prepare`, `BillingKeyDeletionRequestProcessor.requestForWithdrawal`, `MembershipRenewalRules`의 잠금·중단 메서드 |

읽기 계약(`findMemberIdOfMembership`, `findMembershipIds`, `existsBlockingMembership`, 후보 조회 3개, `getProfile`, `hasActiveBillingKey`)은 클래스 기본 `readOnly`이며 바깥 TX가 있으면 합류한다.

### 9-3. PG 호출 전후

```text
트랜잭션 1 (접수)
회원 잠금 → 이용권 C-7 → 회차·시도 또는 환불 REQUESTED 저장
↓
커밋

선점·실행권 TX (시도 PK 또는 환불 PK 하나만)
↓
커밋

PortOne POST → GET
↓
트랜잭션 밖

트랜잭션 2 (결과 반영)
회원 정산 잠금 → 이용권 → 회차 → 시도 → 환불
결제 기록 + 회원 반영 계약(MANDATORY)
↓
같이 커밋
```

회원 반영 계약이 예외를 던지면 결제 기록도 롤백되고 복구가 같은 PortOne ID로 다시 한다.

## 10. 경계를 넘는 결과

| 회원 계약 | 정상 | 멱등 | 충돌 |
|---|---|---|---|
| `applyFirstPaymentPaid` (C-3a) | `ACTIVATED` | `ALREADY_APPLIED` | `CONFLICT` |
| `applyFirstPaymentFailed` (C-3b) | `CLOSED_UNPAID` | `ALREADY_APPLIED` | `CONFLICT` |
| `applyRenewalPaid` (C-4a) | `EXTENDED` | `ALREADY_APPLIED` | `CONFLICT` |
| `stopRenewal` (C-4b) | `RENEWAL_STOPPED` | `ALREADY_APPLIED` | `CONFLICT` |
| `closeTrialConversion` (C-16) | `CONVERSION_CLOSED` | `ALREADY_APPLIED` | `CONFLICT` |
| `terminateAfterRefund` | `TERMINATED` | `ALREADY_APPLIED` | 예외 `MEMBERSHIP-008` |

| 사건 | 이용권(회원) | 결제 |
|---|---|---|
| 구독·전환 접수 | `PAYMENT_PENDING`(전환이면 체험 `autoRenew=false`) | 회차 PENDING, 시도 READY |
| 체험 종료·카드 없음 | 체험 `autoRenew=false` | 행 없음 |
| 첫 결제 성공 | `ACTIVE`(예약 시 `CANCEL_RESERVED`) | PAID |
| 첫 결제 실패 | `TERMINATED`(일반 회원) | FAILED |
| 갱신 성공 | `endAt` 연장 | 회차 PAID |
| 갱신 최종 실패 | `autoRenew=false`, `graceEndAt` 뒤 `EXPIRED` | 회차 FAILED |
| 즉시 해지 접수·불명·실패 | 유지 | 환불 REQUESTED·FAILED |
| 즉시 해지 성공(0원 포함) | `TERMINATED` | 환불 COMPLETED |

## 11. 메서드 계약 — 최종

### 11-1. 회원 도메인 소유

| ID | 메서드 | 역할 | 호출 시점 |
|---|---|---|---|
| — | `MemberService.lockActiveMember(Long memberId)` | 회원 FOR UPDATE, 탈퇴면 `MEMBER-001`. MANDATORY | 신규 거래 TX의 첫 작업 |
| — | `MemberService.lockMemberForSettlement(Long memberId)` → `MemberSettlementLock` | 탈퇴 회원도 잠금. MANDATORY | 정산·복구 TX의 첫 작업 |
| — | `MemberService.getProfile(Long memberId)` → `MemberProfile` | PortOne customer 정보. 없으면 청구하지 않고 READY 유지 | 트랜잭션 밖, 선점 전 |
| C-7 | `MembershipBillingService.getBillingMembershipForUpdate(Long memberId, Long membershipId)` → `BillingMembershipData` | 소유 회원 조건 PK 잠금, 없으면 `MEMBERSHIP-006`. MANDATORY | 회원 잠금 직후 |
| — | `findMemberIdOfMembership(Long membershipId)` → `Long` | 종료·탈퇴 포함 소유 회원 매핑, 잠금 없음 | 웹훅·복구·관리자·환불 실행의 잠금 TX 전 |
| C-8 | `findMembershipIds(Long memberId)` → `List<Long>` | 전체 이용권 ID | C-17 호출 직전 |
| — | `existsBlockingMembership(Long memberId, LocalDateTime now)` | 활성·결제 대기·전환 대기 존재 | 구독 TX1·관리자 지급 |
| — | `existsConflictingMembershipForBilling(Long memberId, Long membershipId, LocalDateTime now)` | 대상 외 활성·대기·후속 이용권. MANDATORY | 갱신 접수·재시도·갱신 PAID 반영 |
| C-12a | `createPendingSubscription(Long memberId, MembershipConsent consent, LocalDateTime requestedAt)` → `Long` | SUBSCRIBE 결제 대기 생성. MANDATORY | 구독 TX1 |
| — | `createPendingConversion(Long memberId, Long trialMembershipId, LocalDateTime now)` → `Long` | 체험 넘김 + TRIAL_CONVERT 결제 대기 생성. MANDATORY | 전환 TX1 |
| C-13 | `findTrialConversionTargets(LocalDateTime now, Long afterMembershipId, int limit)` | 전환 대기 체험 keyset | 전환 스케줄러 |
| C-2 | `findRenewalTargets(LocalDateTime now, Long afterMembershipId, int limit)` | 갱신 후보 keyset | 갱신 스케줄러 |
| — | `findOverdueRenewalTargets(LocalDateTime now, Long afterMembershipId, int limit)` | 마감 누락 후보 | 갱신 정리 |
| C-3a | `applyFirstPaymentPaid(Long membershipId, LocalDateTime pgPaidAt, LocalDateTime endAt, LocalDateTime graceEndAt)` | 첫 결제 활성화. MANDATORY | 청구 TX2 |
| C-3b | `applyFirstPaymentFailed(Long membershipId, LocalDateTime failedAt)` | 결제 대기 이용권 종료. MANDATORY | 청구 TX2 |
| C-4a | `applyRenewalPaid(Long membershipId, LocalDateTime newEndAt, LocalDateTime newGraceEndAt)` | 기간 연장. MANDATORY | 청구 TX2 |
| C-4b | `stopRenewal(Long membershipId)` | 자동갱신 끔. MANDATORY | 청구 TX2·갱신 정리 |
| C-16 | `closeTrialConversion(Long membershipId, LocalDateTime now)` | 카드 없는 체험 종료. MANDATORY | 전환 TX1 |
| — | `terminateAfterRefund(Long membershipId, LocalDateTime completedAt)` | 즉시 종료. MANDATORY | 0원 접수 TX·환불 TX2 |

### 11-2. 결제 도메인 소유

| ID | 메서드 | 역할 | 호출 시점 |
|---|---|---|---|
| C-11 | `MembershipSubscriptionFacade.subscribe(Long memberId, MembershipConsent consent, String idempotencyKey)` → `MembershipSubscribeResult` | 접수 → 청구 → 반영. NEVER | 구독 API |
| C-5 | `MembershipRefundFacade.requestMemberRefund(Long memberId, Long membershipId, String idempotencyKey)` → `MembershipRefundResult` | 접수 → 실행 1회 → 응답. NEVER | 즉시 해지 API |
| C-10 | `BillingKeyService.hasActiveBillingKey(Long memberId)` → `boolean` | ACTIVE 키 존재 | 체험 시작, 회원 잠금 뒤 |
| C-17 | `MembershipPaymentQueryService.existsUnresolved(Collection<Long> membershipIds)` → `boolean` | 미정산 판정. MANDATORY | 회원 잠금 뒤 |
| — | `WithdrawalSettlementChecker.supportedScopes()` / `requireSettled(Long memberId, Long acknowledgedPointBalance)` | 영역 선언·정산 판정. MANDATORY | 탈퇴 최종 TX |
| — | `WithdrawalPaymentMethodPreparer.isEnabled()` / `prepare(Long memberId)` → `WithdrawalPaymentMethodPreparation` | 카드 삭제 접수만. MANDATORY | 탈퇴 준비 TX |

## 12. 관리자 권한

`SecurityConfig`는 `/api/admin/**`를 `SUPER_ADMIN`·`MART_ADMIN`에 열고 Controller가 좁힌다. 결제·환불·포인트·멤버십 관리자 API(`/api/admin/payments`, `/api/admin/membership-payment-attempts`, `/api/admin/membership-refunds`, `/api/admin/members/{memberId}/points`, `/api/admin/members/{memberId}/memberships`)는 모두 `@PreAuthorize("hasRole('SUPER_ADMIN')")`다. 마트 거절 환불(`POST /api/admin/orders/{orderId}/reject`)만 두 Role이 쓰며 [주문 ↔ 결제 연동 기준](order-payment-integration.md)을 따른다.

## 13. 초기 설계(#22)에서 바뀐 점

2026-10-15 FINAL v2 문서 자체는 저장소에 없다. 아래는 저장소의 가장 이른 기준인 초기 요구사항·ERD(`41a9b5f`, #22, 2026-10-20)와 이후 커밋으로 증명되는 변화만 적는다. FINAL v2 본문과의 일대일 대조는 확인 필요다.

| 항목 | 초기 | 현재 | 근거 |
|---|---|---|---|
| 회차와 시도 | `MEMBERSHIP_PAYMENT`에 `billing_key_id`·`portone_payment_id`·`attempt_count`·`last_attempt_at` | `MEMBERSHIP_PAYMENT_ATTEMPT`로 분리, 4개 컬럼 제거 | `55b16b2`(#309), `deploy-implementation.md` C-3 |
| 환불 연결 | 회차에만 연결 | 시도(`membership_payment_attempt_id` NOT NULL)에 연결 | `55b16b2`(#309) |
| 이용권 활성화·유예 | 컬럼 없음 | `activated_at`·`grace_end_at` | `38f7718`(#305) |
| 빌링키 | `status`만 | 발급 요청 테이블, `REPLACED`·`REVOKED`·`DELETE_REQUESTED`·`DELETED` | `2b54711`(#308) |
| 원격 키 삭제·탈퇴 | 없음 | `BILLING_KEY_DELETION`, `AUTO_BILLING` Checker, 탈퇴 준비 | `bdceb66`·`d7bb2f6`(#391) |
| 결제 → 회원 매핑 | 없음 | `findMemberIdOfMembership` | `828acd9`(#323) |
| 정산용 회원 잠금 | 없음 | `lockMemberForSettlement` | `3e8351a`(#365) |
| 미정산 판정 | 없음 | C-17 다섯 조건 | `f52b383`(#309) |

초기 요구사항의 "환불 COMPLETED일 때만 종료", "불명이면 REQUESTED·ACTIVE 유지", "같은 이용권에 REQUESTED 환불이 있으면 다른 키의 새 요청 거절"은 현재 코드와 같다.

## 14. 최종 판정

| 확인 항목 | 판정 |
|---|---|
| 결제가 회원 엔티티·Repository를 직접 쓰지 않음 | 충족 |
| 변경 계약 MANDATORY, PG 경로 NEVER | 충족 |
| 도메인 간 `REQUIRES_NEW` 없음 | 충족 |
| 신규 거래와 정산의 회원 잠금 분리 | 충족 |
| MEMBER 환불 완료와 이용권 종료가 같은 TX | 충족(0원은 접수 TX) |
| C-17이 새 멤버십 거래·해지·탈퇴를 모두 막음 | 충족 |
| 탈퇴 네 Scope | 코드 연결 완료, 운영은 카드 정리 스위치가 false라 `MEMBER-008` 유지 |

| 확인 필요 | 내용 |
|---|---|
| 계약 ID 공백 | C-1, C-6, C-9, C-14, C-15의 정의를 코드·문서에서 찾지 못함 |
| ID 미표기 계약 | `createPendingConversion`(C-12b 여부), `terminateAfterRefund`, `findMemberIdOfMembership`, `existsBlockingMembership`, `existsConflictingMembershipForBilling`, `findOverdueRenewalTargets`, `lockActiveMember`, `lockMemberForSettlement`, `getProfile` |
| FINAL v2 대조 | 원문이 저장소에 없어 13절은 초기 ERD(#22) 기준 |

> 최종 핵심: 결제는 회원 잠금으로 시작해 이용권 → 회차 → 시도 → 환불 순서로 잠그고, 회원 쪽 MANDATORY 계약으로 이용권을 바꿉니다. PortOne은 언제나 커밋 뒤 NEVER 진입점에서 트랜잭션 밖으로 부르며, 미정산이 하나라도 있으면 새 멤버십 거래와 탈퇴를 막습니다.
