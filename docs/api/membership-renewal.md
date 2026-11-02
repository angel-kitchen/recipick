# 멤버십 월 갱신·재시도·카드 대기 (결제 4-A, #342)

첫 결제로 활성화된 유료 이용권을 매월 같은 기준일에 3,900원 자동 청구하고, 실패하면 3일 유예 안에서 24시간 간격으로 최대 3회(PG 발송 기준)
다시 청구한다. 첫 결제 계약·PG 판정·시도 복구는 `docs/api/membership-first-charge.md`, 미정산 판정은 `docs/api/membership-payment-unresolved.md`.
HTTP API는 없다 — 전부 스케줄러(`MembershipBillingScheduledTasks`)와 카드 등록 뒤 재개 경로다.

## 기호

| 기호 | 뜻 | 값 |
| --- | --- | --- |
| T | 이번 갱신이 이어 가는 직전 이용 종료 시각 | 접수 때 잠근 이용권의 `endAt` |
| G | 결제 마감 = T + 3일 | 회차 `graceDeadlineAt`(생성 때 저장, 불변) |
| E | 성공 시 새 이용 종료 시각 | 원래 `billingAnchorDate`로 센 다음 기준일 + 시작 시각의 시·분·초 |
| L · M | 선점 임대 10분 · 복구 여유 2분 | `recipick.membership-billing.lease` / `dispatch-margin` |
| H | 지연 성공 운영 표시 기준 24시간 | `recipick.membership-billing.long-delay` |
| R | 확정 실패 뒤 재시도 간격 24시간 | `recipick.membership-billing.retry-interval` — 정책값과 다르면 기동 실패 |
| N | 회차당 PG 발송 가능 횟수 3 | `recipick.membership-billing.renewal-max-dispatches` — 정책값과 다르면 기동 실패 |

경계는 반열린 구간이다: 새 POST는 `now < G`, 반영은 `pgPaidAt < G` 그리고 `now < E`, 재시도는 `now >= nextRetryAt`.
기준일은 언제나 원래 기준일에서 다시 센다 — 1/31 10:03 활성화 → 2/28 10:03 → 3/31 10:03 → 4/30 10:03.

## 회차와 시도

- 같은 이용기간의 갱신 회차는 하나(`UNIQUE (membership_id, period_start_date)`). 재시도는 같은 회차에 새 시도를 더한다.
- `attemptNo`는 행 순번이다. PG 발송 횟수는 `dispatchedAt`이 남은 시도 수로 센다 — 미발송으로 되돌린 시도와 미발송 CANCELED는 빠진다.
- 시도의 카드(`billing_key_id`)는 접수 때 고정된다. 카드를 바꿔도 접수된 시도는 원래 카드로 끝나고, 다음 시도부터 새 카드다.
- 회차 상태: `PENDING`(시도 진행) · `RETRY_WAITING`(다음 청구 대기, `nextRetryAt` — null이면 "더 청구할 일정 없음, G에서 정리") ·
  `PAYMENT_METHOD_REQUIRED`(ACTIVE 카드 없음, 시도 없음) · `UNKNOWN` · `MANUAL_REVIEW` · 확정 `PAID` / `FAILED`.

## 실행 흐름

```text
runOnce (5분, 플래그 ON에서만)
  ① C-2 후보(PAID 활성·autoRenew·해지 예약 없음·endAt ≤ now < graceEndAt) → accept
  ② 재개 후보(마감 전 RETRY_WAITING·nextRetryAt 도달 / PAYMENT_METHOD_REQUIRED / PENDING) → resumeExisting(원래 회차 ID)
  → CHARGE_READY면 공용 실행기: 선점 TX → PG POST → 같은 ID GET → TX2
settleOnce (5분, 플래그 무관)
  마감 지난 미해결 회차 + 마감 전 대기 회차 → settleExisting: 만료 READY 취소·해지/마감/한도 종료·PENDING 잔존 정규화. PG·새 시도 없음
recoverCharges (5분, 플래그 무관, 3단계와 같음)
  오래된 DISPATCHING·UNKNOWN 같은 ID 재조회, 만료 READY 처리(갱신 회차는 아래 규칙으로 이어 간다)
카드 등록(POST /api/billing-keys) 커밋 뒤 → resumeForMember: 그 회원의 카드 대기 회차만 재개. 실패해도 등록 응답은 성공
```

잠금 순서는 `MEMBER → MEMBERSHIP → MEMBERSHIP_PAYMENT → 그 회차의 ATTEMPT들을 PK 오름차순으로 → 환불`. 판정 시각은 잠금을 모두 잡은 뒤 읽는다.
PG 호출은 트랜잭션 밖이고, 결과 기록과 이용권 반영(C-4a/C-4b)은 한 TX2다.

### 신규 접수 (`MembershipRenewalProcessor.accept`)

1. 회원 잠금 → 이용권 잠금(C-7) → 유료·구독/전환·활성·미종료인지 → 기간 계산(원래 기준일과 어긋나면 접수하지 않고 운영 로그)
2. 이번 기간 회차가 있으면 **그 회차를 이어 간다**(아래). 미정산 검사보다 먼저 — 자기 대기 회차 때문에 막히지 않게
3. 없으면: autoRenew·해지 예약 없음·`T ≤ now < G` → 동의 금액 3,900·문구·시각 → 플래그 → C-17(이 회원 전체 이용권) → ACTIVE 카드
4. 카드 없음 → `PAYMENT_METHOD_REQUIRED` 회차(시도 0) / 있음 → `PENDING` 회차 + READY #1(임대 = min(now+L, G))

### 기존 회차 이어 가기 (`resumeExisting` · READY 만료 · 실패 뒤 · 정리 공통 — `MembershipRenewalRules`)

```text
시도 전부 PK 오름차순 잠금 → fresh now
확정 회차 → 그대로 / 자동 정합화 중단 표시 → 멈춤
만료 READY → CANCELED(미발송 보장)
DISPATCHING·UNKNOWN·MANUAL_REVIEW 있음 → 새 시도·종료 금지(재조회가 맡는다)
접수 때 유효했던 READY 하나 → 그 시도 실행(해지 예약만으로 버리지 않는다. 이용권 종료·기간 불일치면 실행하지 않는다)
설명되지 않는 PAID 시도가 남음 / 이용권 기간이 회차 T와 다름 → 멈춤(운영 확인, 변경 없음)
종료 조건(대기보다 먼저): 이용권 종료 → MEMBERSHIP_TERMINATED / 해지 예약·autoRenew=false → RENEWAL_CANCELED
                           / now ≥ G → RENEWAL_DEADLINE_PASSED / 발송 ≥ 3 → RENEWAL_ATTEMPTS_EXHAUSTED
   → 회차 FAILED + C-4b(자동갱신만 끔. graceEndAt·endAt은 그대로)
PENDING인데 살아 있는 시도 없음(미발송 만료 잔존) → RETRY_WAITING으로 정규화. 재접수 가능 시각 = 마지막 FAILED 확정 + R(실패 없으면 지금)
정리 작업이면 여기까지
nextRetryAt 전·null → 대기 / 동의·금액 → 플래그 → 재시도 가드(C-17 네 조건 중 자기 RETRY_WAITING·카드 대기 한 행만 제외) → ACTIVE 카드
카드 없음 → PAYMENT_METHOD_REQUIRED(nextRetryAt 보존) / 있음 → READY(attemptNo = 최대 + 1, 임대 = min(now+L, G)) + PENDING
```

### 결과 반영 (TX2, `MembershipChargeResultProcessor`) — 갱신 회차

| 결과 | 처리 |
| --- | --- |
| 확정 시도에 다시 온 결과 | 같은 PAID 시도의 PAID는 멱등. FAILED·CANCELED 시도의 PAID는 상태를 바꾸지 않고 멈춤(운영 확인, 추정 환불 없음) |
| 확정 회차의 다른 시도가 PAID | 회차·이용권 그대로, 그 시도 PAID + `PAID_ON_CLOSED_PAYMENT` + SYSTEM 환불 REQUESTED. 자동갱신은 끄지 않는다 |
| `pgPaidAt < T` / `pgPaidAt > now` | `PAID_AT_BEFORE_PERIOD` / `PAID_AT_IN_FUTURE`로 MANUAL_REVIEW. 연장·환불·시각 보정 없음 |
| 이용권이 종료·비유료·미활성 | 연장 없음, `PAID_CONFLICT` 환불 요청. C-4b는 끌 수 있는 이용권(유료·활성·구독/전환)일 때만 |
| 이용권 `endAt ≠ T` | 정합성 예외 → TX2 롤백. 복구가 원인을 재검증해 자동 정합화 중단 표시 |
| KRW 수납액 ≠ 3,900 | 실제 수납액 SYSTEM 환불 REQUESTED, 회차 FAILED, C-4b |
| `pgPaidAt ≥ G` | `LATE_PAID_AFTER_DEADLINE` 환불 요청, 회차 FAILED, C-4b |
| `now ≥ E` | `RENEWAL_PERIOD_ENDED` 환불 요청, 회차 FAILED, C-4b |
| 정상 | C-4a(E, E+3일) `EXTENDED`만 성공. 시도 PAID + 회차 PAID + `membershipAppliedAt`. `now − pgPaidAt ≥ H`면 `RENEWAL_LONG_DELAY` 표시 |
| C-4a ALREADY_APPLIED / 사유 없는 CONFLICT | 정합성 예외 → 롤백·운영 확인 |
| FAILED | 시도 FAILED(확정 시각 = now) → 위 "이어 가기"의 종료 조건 → 아니면 `nextRetryAt = 확정 + R`(G 이상이면 null) RETRY_WAITING |
| UNKNOWN·404·조회 장애 / 신뢰할 수 없는 결과 | 첫 결제와 같음(같은 ID 재조회 / MANUAL_REVIEW). 새 ID 없음 |

해지 예약 상태의 성공은 C-4a가 `graceEndAt`을 null로 두고 예약·autoRenew=false를 유지한다. 유예가 지난 EXPIRED 이용권도 조건을 통과하면 연장한다.

## 예시 (T = 11/06 10:00)

| 시각 | 결과 | 회차 | 혜택 |
| --- | --- | --- | --- |
| 11/06 10:00 #1 FAILED | RETRY_WAITING, nextRetryAt 11/07 10:00 | 미해결 | 유지(graceEndAt 11/09 10:00) |
| 11/07 10:00 #2 FAILED | RETRY_WAITING, nextRetryAt 11/08 10:00 | 미해결 | 유지 |
| 11/08 10:00 #3 FAILED | FAILED `RENEWAL_ATTEMPTS_EXHAUSTED`, autoRenew=false | 확정 | 11/09 10:00까지 유지 뒤 EXPIRED |
| 11/08 10:00 #3 UNKNOWN, 11/09 10:00 도달 | 정리 작업은 회차를 닫지 않는다(UNKNOWN 유지, 재조회 계속) | 미해결 | 시간 만료 |
| #2가 11/08 11:00에 FAILED | 11/09 11:00 ≥ G → nextRetryAt null | 미해결 | 11/09 10:00 정리에서 FAILED + C-4b |
| 처음부터 카드 없음 → 11/07 등록 | 등록 커밋 뒤 같은 회차에서 1회 청구 | — | — |
| #1 FAILED 뒤 카드 교체 | 24시간 예약 그대로. 11/07 10:00의 새 시도부터 새 카드 | — | — |

## 운영 차단·활성화

- `MEMBERSHIP_BILLING_ENABLED=false`(운영 기본)면 신규 접수·재시도·카드 대기 재개·READY 재발급·PG POST가 멈춘다. 갱신 회차 정리(`settleOnce`)와
  시도 복구는 계속 돈다. 플래그가 3일 넘게 꺼져 있으면 그 달 갱신은 G에서 `RENEWAL_DEADLINE_PASSED`로 끝난다 — 켤 때는 이 점을 보고 결정한다.
- 4-A 머지는 운영 갱신 활성화가 아니다. 활성화 조건은 `docs/api/membership-first-charge.md`의 운영 활성화 게이트와 같고, 4-B(웹훅·전체 Sweep·
  마감 놓친 이용권·확정 시도 수납 정정·탈퇴 Checker)와 5단계(SYSTEM 환불 실행)가 끝나기 전에는 켜지 않는다.
- 스케줄러 전용 풀은 스레드 2개다. 청구 작업 두 개가 둘 다 PG 지연에 걸리면 정리 작업도 늦어질 수 있다. 주기 5분은 SLA가 아니다 —
  혜택 만료는 회원 도메인의 시각 판정으로 일어나고, 회차 정리가 정확히 G에 돈다고 가정하지 않는다.
- 인덱스 2개(`idx_membership_payment_status_next_retry_id`, `idx_membership_payment_status_grace_id`)는 `docs/deploy-implementation.md` 10-F.

## 4-A에서 하지 않은 것

- 회차 없이 마감을 놓친 이용권 탐색, 다른 이용권 충돌 판정, 탈퇴 회원 후속 정산, 멤버십 웹훅 라우팅, 결과 불명 창(W) 전이, 확정 시도의
  수납 정정, 빌링키 REVOKED — 4-B에서 반영(`docs/api/membership-reconciliation.md`)
- SYSTEM 환불의 실제 PG 실행·완료 반영 — 5단계. 4-A가 만드는 환불 `REQUESTED`는 완료가 아니며 C-17이 계속 막는다
