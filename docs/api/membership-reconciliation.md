# 멤버십 결제 웹훅·정합화·복구 (#343)

4-A(월 갱신·재시도·카드 대기) 위에 이미 생긴 결제의 결과 확인·수납 정정·차단·복구를 더한다. 새 청구 정책(금액·R·N·G·E)은 바꾸지 않는다.
`MEMBERSHIP_BILLING_ENABLED`는 새 청구(접수·재시도·POST)만 막고, 이 문서의 경로는 플래그와 무관하게 돈다.

## 재조회 원인과 GET 허용

같은 결제 ID의 `GET /payments/{id}`만 한다. 어떤 원인이든 POST는 없다.

| 시도 상태 | 복구 Sweep·실행기(`SCHEDULED`) | 검증된 웹훅(`VERIFIED_WEBHOOK`) | 운영자 재조회(`ADMIN_RECHECK`) |
| --- | --- | --- | --- |
| 회차에 강한 차단 시도가 있음 | GET 없음 → `RECONCILE_HALTED` | GET 없음 → `RECONCILE_HALTED` | GET만 하고 반영 없음 → `RECONCILE_HALTED` + 관측값 |
| READY | GET 없음 → `NOT_DISPATCHED` | GET → 판정 | GET → 판정 |
| DISPATCHING 신선(`dispatchedAt + L + M` 전) | GET 없음 → `IN_PROGRESS` | GET → 판정(미확정이면 상태 유지·재시도) | 같음 |
| DISPATCHING 오래됨·UNKNOWN | GET → 판정 | GET → 판정 | GET → 판정 |
| MANUAL_REVIEW | GET 없음 | GET → 판정 | GET → 판정 |
| PAID·FAILED·CANCELED | GET 없음 → `ALREADY_APPLIED` | GET → 판정 | GET → 판정 |

## 결과 반영(TX2) 판정 순서

1. 잠금: 회원(정산 잠금 — 탈퇴 회원 포함) → 이용권(C-7) → 회차 PK → 회차의 시도 전부 PK 오름차순(첫 결제·갱신 공통). 판정 시각은 잠금 뒤
2. 회차에 강한 차단 시도가 하나라도 있음 → 반영 없음 `RECONCILE_HALTED`
3. GET 전에 본 선점 시각과 지금 선점 시각이 다르고 관측이 신뢰 가능한 PAID가 아님 → `IN_PROGRESS`
4. PG에 보낸 적 없는 시도(`dispatchedAt` 없음): 아래 미발송 표
5. PG에 보낸 확정 시도(PAID·FAILED): 아래 확정 표
6. 미해결 시도: 4-A 판정 + 결과 불명 창 + 탈퇴·충돌 분기

### 미발송 시도 × PG 관측

| PG 관측 | 처리 |
| --- | --- |
| 신뢰 가능한 PAID | 강한 차단 `UNDISPATCHED_PAID`. 상태 유지·환불·연장 없음 |
| 수납이 아닌 거래 객체(FAILED·PENDING·READY·취소·ID 불일치 등) | 강한 차단 `UNDISPATCHED_PG_ACTIVITY`. 금액 추정 없음 |
| 404 | PG에 거래 없음 확인. READY → `NOT_DISPATCHED`, CANCELED·FAILED → `ALREADY_APPLIED` |
| 조회 장애·timeout·5xx | 존재도 부재도 증명하지 못함. 변경 없음 `STILL_UNKNOWN`(웹훅은 재시도) |

### 확정 시도 × PG 관측

| 시도 | PG 관측 | 처리 |
| --- | --- | --- |
| PAID(반영됨) | 같은 결제 시각·금액 + 회차 PAID·반영 시각·결제 시각 일치 | 멱등 `ALREADY_APPLIED` |
| PAID(반영됨) | 같은 근거인데 회차 반영 근거가 맞지 않음 | 강한 차단 `APPLIED_STATE_INCONSISTENT`(재반영·새 환불 없음) |
| PAID(환불 필요) | 같은 근거 | SYSTEM 환불 행 확인·보충만 `ALREADY_APPLIED` |
| PAID | 다른 결제 시각·금액, 또는 FAILED | 강한 차단 `PAID_EVIDENCE_CONFLICT`(PAID를 내리지 않음) |
| FAILED(발송됨) | 신뢰 가능한 PAID, 결제 시각이 회차 기간 안(갱신: 이전 종료 시각 이후·현재 이전) | 수납 정정: 상태 PAID·환불 필요·`LATE_PAID_AFTER_FAILED`, 최초 실패 사유·확정 시각 보존, 실제 수납액 SYSTEM 환불. 연장 없음. 환불 대상 수납만 남은 미확정 갱신 회차는 `UNAPPLIED_PAID_ONLY`로 닫는다(같은 회차 새 청구 없음) |
| FAILED(발송됨) | 신뢰 가능한 PAID인데 결제 시각이 회차 기간 전이거나 미래 | 강한 차단 `PAID_AT_INVALID`(FAILED 이력 유지, 정정·환불 없음) |
| 확정 시도 | 조회 장애·404 | 변경 없음 `STILL_UNKNOWN` — 웹훅은 재시도로 남긴다(처리 완료로 기록하지 않음) |
| 확정 시도 | 신뢰 불가 응답 | 변경 없음 `MANUAL_REVIEW`(운영 확인) |
| 그 밖 | 취소 상태 등 | 변경 없음 `ALREADY_APPLIED` |

### SYSTEM 환불 멱등

같은 시도의 SYSTEM 환불 행을 상태와 무관하게 본다. 0건이면 순번 1 생성. 금액이 모두 같고 FAILED가 아닌 행이 1건 이하면 그대로(REQUESTED·
FAILED·COMPLETED 어느 것이든 새 요청 없음 — FAILED 뒤 재집행은 5단계). 그 밖이면 강한 차단 `PAID_EVIDENCE_CONFLICT`이고, 그 차단은 호출한
판정의 반환값까지 `RECONCILE_HALTED`로 올린다(차단 표시는 커밋, 뒤따르는 회차 정리·상태 변경은 하지 않는다). **REQUESTED는 환불 완료가
아니다** — C-17 ③·④가 계속 막는다.

### 결과 불명 창(W = 발송 시각 + 24시간)

미확정 결과(404·조회 장애·진행 중)에서: 웹훅·운영자 재조회가 신선한 DISPATCHING을 보면 상태 유지. W가 지났으면 이번 GET이 마지막 자동
조회 — 시도·회차 MANUAL_REVIEW + `UNKNOWN_WINDOW_EXPIRED`(차단 아님. 웹훅·운영자 재조회는 계속 판정). 그 외 UNKNOWN 유지. W 기준은 발송 시각
하나다(마지막 조회·갱신 시각으로 늘리지 않는다). W 만료는 실패 확정도 새 시도 사유도 아니다.

### 갱신 PAID의 탈퇴·충돌

확정 회차 → T 이전 결제 → 미래 결제 → **탈퇴 회원: 미반영 환불(`PAID_AFTER_WITHDRAWAL`)** → 비유료·종료 이용권 → **다른 이용권 충돌: 미반영
환불(`CONFLICTING_MEMBERSHIP`) + 자동갱신 끔** → 기간 불일치 → 금액 → G → E → 연장.

## 강한 정합성 차단

`APPLIED_STATE_INCONSISTENT`·`UNDISPATCHED_PAID`·`UNDISPATCHED_PG_ACTIVITY`·`PAID_EVIDENCE_CONFLICT`·`PAID_AT_INVALID`. 차단은 회차 단위로 작동한다 — 회차의 어느
시도든 차단이면: 선점 거절(다른 READY 포함), POST 직전 재확인으로 미발송 되돌림, READY 만료 처리 제외(자동 CANCELED·새 시도 없음),
복구 조회 제외, TX2 반영 없음, C-17 ⑤로 탈퇴 거절. 약한 표시 뒤에 오면 올려 쓰고, 이미 차단이면 첫 차단을 유지한다. 운영 API에 표시 해제·
강제 PAID는 없다(운영이 PortOne과 대조한 뒤 별도 조치).

선점이 커밋된 뒤 다른 시도에서 차단이 확정되는 경합은 PG 호출이 TX 밖이라 완전히 없앨 수 없다. POST 직전 재확인이 보조하고, 결과 반영은
TX2의 회차 전체 재확인이 막는다.

## 마감 누락 이용권

`settleRenewals`(5분, 플래그 무관)가 결제 마감(graceEndAt)이 지났는데 자동갱신이 켜진 구독·전환 이용권을 찾는다. 이번 기간 회차가 있으면
기존 정리로 잇고, 없으면 미정산이 없을 때만 FAILED 회차 1건(`RENEWAL_MISSED_NO_ATTEMPT`, 시도 0건) + 자동갱신 끔. 다른 이용권 충돌이 있어도
같다. 소급 청구·반복 생성 없음. 미정산이 있으면 다음 주기로 미룬다.

## 탈퇴 회원

이미 시작된 거래의 결과 확인·수납 정정·환불 요청·W 전이·차단·회차 정리·마감 누락 정리·키 폐기는 탈퇴 뒤에도 진행한다(정산 잠금).
새 혜택(연장·활성화)·새 시도·새 접수는 만들지 않는다. 정상 반영된 PAID의 재전송은 멱등이다.

첫 결제도 같다. 탈퇴 뒤 확인된 첫 결제 수납은 활성화하지 않고 실제 수납액을 SYSTEM 환불 요청(`PAID_AFTER_WITHDRAWAL`)하며, 결제 대기
이용권(PAID·미활성·SUBSCRIBE/TRIAL_CONVERT)은 종료한다. 탈퇴 회원의 첫 결제 READY 임대 만료는 유효한 다른 READY를 실행 대상으로
돌려주지 않고(임대 만료 정리에 맡긴다) 새 시도도 만들지 않는다 — 회차는 재접수 불가로 닫힌다.

## 빌링키 폐기(REVOKED)

| 진입 | 조건 |
| --- | --- |
| 실행기 | 청구 POST 응답이 "사용할 수 없는 키"면 결제 판정과 별개로 그 키만 조회 |
| 복구 Sweep | W 안의 `UNKNOWN` + `PAYMENT_NOT_FOUND` 시도에 묶인 ACTIVE·REPLACED 키를 키별 1회 조회 |

PortOne이 `DELETED`로 답하거나 404 + `BILLING_KEY_NOT_FOUND`일 때만 REVOKED. 다른 404·401·403·5xx·timeout은 변경 없음. 2xx 응답의
`billingKey`가 요청한 키와 다르면 `BILLING_KEY_MISMATCH`(변경 없음). 실행기의 키 확인은 결제 결과 반영이 실패해도 수행하고, 키 확인 실패는
격리한다(결제 쪽 예외는 그대로 올라간다). 반영은 회원 정산 잠금 →
키 PK 잠금 → 소유 확인 → 그 키 한 행만. 결제 시도 상태는 바꾸지 않는다(UNKNOWN은 같은 ID 재조회 계속). REVOKED·DELETE_REQUESTED·DELETED 키로
접수된 READY는 선점하지 않는다(REPLACED는 허용).

## 웹훅

기존 Inbox(claim → 판정 → 결과 기록)를 그대로 쓴다. 결제 ID가 `mpay_`로 시작하면 상품 결제 조회 전에 멤버십 분기로 간다(매핑은 UNIQUE 결제 ID
정확 일치).

| 상황 | Inbox 결과 |
| --- | --- |
| 시도 매핑 없음 | MANUAL_REVIEW(PG 조회 없음) |
| 취소 계열(Cancelled·PartialCancelled·CancelPending) | MANUAL_REVIEW `MEMBERSHIP_REFUND_RECONCILIATION_PENDING` — 5단계가 이 사유로 다시 처리 |
| Paid·Failed → 반영·멱등·환불 요청 | PROCESSED |
| 미확정·신선한 선점·READY | RETRY_PENDING |
| 신뢰 불가·차단 | MANUAL_REVIEW |

## 운영 API (SUPER_ADMIN)

| 메서드·경로 | 감사 |
| --- | --- |
| `GET /api/admin/membership-payment-attempts?status=&membershipId=&opsReviewOnly=&blockedOnly=&page=&size=` | `MEMBERSHIP_PAYMENT_LIST_VIEW` |
| `GET /api/admin/membership-payment-attempts/{attemptId}` | `MEMBERSHIP_PAYMENT_DETAIL_VIEW` |
| `POST /api/admin/membership-payment-attempts/{attemptId}/recheck` | `MEMBERSHIP_PAYMENT_RECHECK`(GET 전에 기록·커밋) |

재조회 응답: `outcome`, 재조회 뒤 `status`·`paymentStatus`·`opsReviewReason`·`settlementBlocked`·`blockedReason`, 이번 GET의 `pgVerdictKind`
(조회했을 때만)·`pgPaidAt`·`pgPaidAmount`(신뢰 가능한 수납일 때만). 요청 본문으로 결제 ID·금액·상태를 받지 않는다. 빌링키·고객 정보·PG 원문은
어느 응답에도 없다. 없는 시도는 `BILLING-003`(404), 차단 상태 재조회는 200 + `RECONCILE_HALTED`.

## 탈퇴 정산 Checker

`MembershipPaymentSettlementChecker`(`MEMBERSHIP_PAYMENT`, `@Order(200)`)는 C-17 다섯 조건이 하나라도 있으면 `BILLING-004`(409)로 탈퇴를 거절한다.
`AUTO_BILLING` 영역은 6단계라 탈퇴는 그 전까지 계속 열리지 않는다(`WITHDRAW_SETTLEMENT_NOT_READY`).

## 이관

| 단계 | 항목 |
| --- | --- |
| 5단계 | SYSTEM 환불 PG 실행·완료/실패 반영, FAILED 환불 재집행, 취소 계열 웹훅 반영, MEMBER 환불 |
| 6단계 | 원격 빌링키 삭제, `AUTO_BILLING` Checker, 탈퇴 개방 |

## 회원 계약(회원 도메인 제공)

| 메서드 | 쓰는 곳 |
| --- | --- |
| `MemberService.lockMemberForSettlement(memberId)` → `(memberId, withdrawn)` | TX2·READY 만료·정합성 표시·회차 정리·마감 누락 정리·키 폐기 |
| `MembershipBillingService.findOverdueRenewalTargets(now, afterId, limit)` | 마감 누락 후보 |
| `MembershipBillingService.existsConflictingMembershipForBilling(memberId, membershipId, now)` | 갱신 접수·재접수·늦은 갱신 PAID |

신규 접수(바로 구독·체험 전환·갱신 접수·같은 회차 재접수·카드 등록)는 계속 `lockActiveMember`(탈퇴 거절)다.
