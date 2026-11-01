# 멤버십 첫 결제 (바로 구독·체험 전환)

멤버십 첫 유료 결제를 중복 청구 없이 접수·실행하고, PortOne 재조회로 확정된 결과를 이용권에 정확히 한 번 반영하는 결제 도메인 계약입니다.
3차 3단계(#325) 기준. 월 갱신·재시도·웹훅·탈퇴 Checker는 4단계, SYSTEM 환불의 PG 실행은 5단계, 빌링키 PortOne 삭제는 6단계입니다.

## 계약 (C-11)

- `MembershipSubscriptionFacade.subscribe(Long memberId, MembershipConsent consent, String idempotencyKey)` → `MembershipSubscribeResult(membershipId, status)`
- `@Transactional(propagation = NEVER)` — **회원 Facade는 바깥 트랜잭션 없이 호출합니다.** 트랜잭션 안에서 부르면 접수 행을 만들거나 PG를
  부르기 전에 `IllegalTransactionStateException`으로 거절됩니다. 필요한 쓰기 트랜잭션(TX1·선점·TX2)은 결제 Processor가 시작합니다.
- 결제 쪽에는 구독 HTTP API가 없습니다. 구독 진입 API와 응답 매핑은 회원 도메인이 소유합니다(아래 협업 항목).
- 같은 원칙: `MembershipChargeExecutor.execute`·`reconcile`, `TrialConversionFacade.runOnce`, `MembershipChargeRecoveryFacade.recoverOnce`도 `NEVER`.

| `MembershipSubscribeStatus` | 뜻 | 회원 안내(회원 도메인이 매핑) |
| --- | --- | --- |
| `PAID` | 첫 결제 확정, 이용권 활성 | 구독 완료 |
| `IN_PROGRESS` | 접수됨·처리 중(READY·DISPATCHING, 플래그 OFF로 미발송 포함) | 결제 확인 중, 같은 키로 다시 조회 |
| `FAILED` | 확정 실패 또는 반영 불가(환불 요청). 같은 키로 재청구하지 않음 | 결제 실패, 새 구독은 새 키로 |
| `UNKNOWN` | 결과 불명·운영 확인(자동 정합화 중단 포함) | 결제 확인 중, 고객센터 안내 |

## 오류 코드

| 코드 | 상황 |
| --- | --- |
| `MEMBERSHIP-002` | 활성·결제 대기·전환 대기 이용권이 이미 있음 |
| `MEMBERSHIP-004` | 자동결제 동의 없음 또는 동의 금액이 3,900원이 아님 |
| `MEMBERSHIP-005` | ACTIVE 빌링키 없음 |
| `MEMBERSHIP-009` | 미해결·미정산 멤버십 결제가 있음(C-17) |
| `COMMON-007` / `COMMON-001` | 멱등 키 누락 / 키 형식 오류(1~64자, 공백 없음) |
| `COMMON-011` | 같은 키로 다른 요청 내용(동의 금액·문구 버전) |
| `BILLING-009` (503) | 기능 플래그 OFF에서 **새 접수**일 때만. 기존 키의 결과 조회는 플래그와 무관 |

## 멱등

- 저장 키는 `"{memberId}:{소문자 키}"` — 다른 회원의 회차는 조회되지 않습니다. 회차 `idempotency_key` UNIQUE가 최종 방어입니다.
- 요청 해시(`MembershipRequestHash`)는 동의 금액·문구 버전만 포함합니다(전송 시각 제외). 최초 접수 때 저장한 동의 기록은 바꾸지 않습니다.
- TX1은 **MEMBER 잠금 → 멱등 키 조회** 순서입니다. 키 조회가 이용권 중복 검사(002)보다 먼저라서, 같은 요청을 다시 보낸 회원이 자기 결제 대기
  이용권 때문에 409를 받지 않습니다.

| 기존 회차·시도 | 같은 키 재요청 |
| --- | --- |
| PENDING + READY | 같은 저장 시도의 실행권 확보를 다시 시도 |
| PENDING + DISPATCHING | `IN_PROGRESS`, 새 POST 없음 |
| UNKNOWN · MANUAL_REVIEW | `UNKNOWN`, 새 결제 ID 없음 |
| PAID / FAILED | 저장된 결과 반환, 재청구 없음 |
| 다른 요청 해시 | 409 `COMMON-011` |

## 실행 흐름

```text
TX1     MEMBER 잠금 → 멱등 키 → (새 접수만) 플래그 → 002 → C-17(009) → 빌링키(005) → 동의(004) → C-12a 결제 대기 이용권
        → 회차 PENDING → 시도 READY("mpay_" + UUID, 빌링키 FK 고정, lease = min(now + L, 첫 시도 + 30분)) → COMMIT
(TX 밖) 회원 결제용 프로필 조회 — 실패하면 PG 미호출, 시도 READY 유지
선점 TX  시도 PK만 잠금 → READY·lease 유효 → DISPATCHING(dispatchedAt, 마이크로초 절삭) → COMMIT
(TX 밖) POST 직전 플래그·첫 접수 유효기간 재확인 → 통과 시 POST /payments/{paymentId}/billing-key
(TX 밖) 같은 paymentId로 GET — 결과 확정은 GET으로만
TX2     MEMBER → MEMBERSHIP(C-7) → MEMBERSHIP_PAYMENT → ATTEMPT 잠금 → 결과 기록 + 이용권 반영 함께 COMMIT(I-22)
```

- POST 응답(200·409·4xx·5xx·timeout)은 참고 정보이고 성공·실패를 확정하지 않습니다. POST 직전 검사에서 막히면 같은 선점자만
  `releaseUnsent`로 READY에 되돌립니다(시도 ID·상태·`dispatchedAt` 일치 확인, 실패하면 손대지 않고 정합화 경로로).
- 선점에 진 Worker가 본 신선한 DISPATCHING(`dispatchedAt + L + M` 전)은 GET하지 않고 `IN_PROGRESS`로 끝납니다.
- 첫 결제 회차는 회차의 `graceDeadlineAt == null`(`MembershipPayment.isFirstPayment()`)로 판별합니다. 이용권의 `activatedAt`으로 판별하지 않습니다.

## PG 결과 판정 (`MembershipPgVerdict`)

모든 판정은 **결제 ID 일치 확인이 먼저**입니다. 다르면 FAILED여도 신뢰하지 않습니다.

| GET 결과 | 판정 | 처리 |
| --- | --- | --- |
| PAID, ID·`paidAt`·금액·통화 존재, KRW 3,900 | `PAID` | 시도·회차 PAID + C-3a 활성화 + `membershipAppliedAt` |
| PAID, KRW, 금액 ≠ 3,900 | `PAID_AMOUNT_MISMATCH` | 반영 안 함, 운영 표시, **실제 수납 KRW 금액**으로 SYSTEM 환불 REQUESTED |
| PAID, 통화가 KRW가 아님 / 금액·통화·`paidAt` 누락 | `UNTRUSTED` | MANUAL_REVIEW, **자동 환불 없음** |
| FAILED(ID 일치) | `FAILED` | 시도·회차 FAILED + C-3b 대기 이용권 종료 |
| READY·PENDING 등 진행 중, 조회 오류·timeout | `UNRESOLVED` | 시도 UNKNOWN, 같은 ID 재조회 |
| 404 | `NOT_FOUND` | UNKNOWN 유지, **새 결제 ID 금지**(E-4 전) |
| ID 불일치·CANCELED·PARTIAL_CANCELED·해석 불가 | `UNTRUSTED` | MANUAL_REVIEW |

| 첫 결제 PAID의 이용권 쪽 상황 | 처리 |
| --- | --- |
| 1회차 기간 남음 | 활성화. 확인 지연이 D(24시간) 이상이면 운영 표시 |
| 1회차 기간이 이미 끝남 | 활성화 안 함, 회차 FAILED, C-3b, 전액 SYSTEM 환불 REQUESTED |
| C-3a `CONFLICT`(이용권 종료 등, 사유 확인됨) | 반영 안 함, SYSTEM 환불 REQUESTED |
| 같은 이용권에 이미 반영된 다른 회차가 있음 | 중복 수납 → SYSTEM 환불 REQUESTED |
| 이용권은 이미 활성인데 반영된 회차가 없음(정합성 오류) | **상태 전이·환불 없이 TX2 롤백**(ERROR 진단). 미해결 그대로 C-17이 막음 |

- 환불 REQUESTED는 시도 결과·회차 종료와 **같은 TX2**에서 저장합니다. 저장이 실패하면 전부 롤백됩니다.
- 시스템 환불의 PG 실행은 5단계입니다. COMPLETED 전까지 C-17은 미정산으로 판정합니다.
- 확정된 회차(PAID·FAILED)는 예상 밖 결과가 와도 상태를 바꾸지 않습니다.

## 첫 결제 기간 (`MembershipBillingPeriods`)

- 이용 시작은 서버 확인 시각이 아니라 **PG `paidAt`(KST 변환값)**입니다. `start_at = activated_at = paidAt`, 기준일 = `paidAt` 날짜.
- `end_at(1) = anchorDate(2) + paidAt의 시·분·초`, `grace_end_at = end_at + 3일`(해지 예약이 있으면 C-3a가 null로 반영).
- 기준일은 항상 원래 기준일에서 계산합니다(1/31 → 2/28 → 3/31). JVM 기본 시간대가 UTC여도 결과가 같습니다.

## READY 만료 (공통 규칙, 4단계 재사용)

첫 접수 유효기간은 **첫 시도 `requestedAt` + 30분**이고 재발급해도 다시 시작하지 않습니다. 회차당 시도는 CANCELED 포함 **최대 3개**입니다.

```text
READY 만료 → CANCELED (실패하면 최신 상태별 결과: IN_PROGRESS / UNKNOWN·MANUAL_REVIEW / 이미 확정)
  ├─ 회차가 이미 PAID·FAILED → 변경 없음
  ├─ 다른 DISPATCHING·UNKNOWN·MANUAL_REVIEW 시도 있음 → 새 시도 없음, 회차 FAILED 없음(기존 시도 정합화)
  ├─ 다른 유효 READY 있음 → 그것을 사용, 새 ID 없음
  └─ 다른 미해결 시도 없음
        ├─ 플래그·이용권·해지 예약·빌링키·30분·3개 모두 통과 → 새 READY(새 attemptNo·새 paymentId)
        └─ 불가 → 회차 FAILED + C-3b(대기 이용권 종료). 재구독은 새 동의·새 접수로
```

## 최소 복구 (`MembershipChargeRecoveryFacade`, 5분 간격)

| 대상 | 처리 |
| --- | --- |
| READY, lease 만료 | 위 READY 만료 규칙 |
| DISPATCHING, `dispatchedAt + L + M` 경과 | 같은 paymentId로 GET → TX2. POST 다시 안 함 |
| UNKNOWN | 같은 paymentId로 재조회. W(24시간) 초과 MANUAL_REVIEW 전이는 4단계 |
| 회원 찾기 | `findMemberIdOfMembership(membershipId)`(잠금 없음) → MEMBER 잠금 → C-7로 소유권 재검증. 이용권이 없으면(MEMBERSHIP-006) 안전 중단·ERROR |

- **자동 정합화 중단**: 시도의 `failureReason = APPLIED_STATE_INCONSISTENT` **그리고** 운영 표시(`opsReviewRequiredAt`)가 함께 있을 때만
  (`MembershipPaymentAttempt.isAutoReconcileHalted`). 스케줄러 조회·`reconcile`·TX2 진입이 모두 같은 판정을 씁니다. 다른 운영 표시 사유는 복구를 멈추지 않습니다.
- 중단된 시도의 처리 절차: 원인 확인 → 데이터 정합성 확인 → 조치 기록 → 재처리. **운영 표시만 지우는 것은 해결이 아니며, C-17 정산 완료를 뜻하지 않습니다.**

## 기능 플래그 `recipick.membership-billing.enabled` (환경변수 `MEMBERSHIP_BILLING_ENABLED`, 운영 기본 false)

| 작업 | OFF |
| --- | --- |
| 바로 구독 새 접수 | 차단(`BILLING-009`) |
| 같은 키 기존 결과 조회 | 허용 |
| 체험 전환 새 접수, READY 재발급 | 차단 |
| READY 시도의 PG POST | 차단(POST 직전 재확인) |
| DISPATCHING·UNKNOWN 재조회, 확인된 결과의 TX2, 환불 REQUESTED 저장 | 허용 |

## 운영 활성화 게이트 (모두 충족 + 검수 GO 전에는 플래그를 켜지 않음)

1. PortOne 실험 E-3(빌링키 결제 응답·`paidAt`·`mpay_` ID 규칙), E-4(거절 시 결제 레코드 존재·404 의미), E-6(같은 ID 재시도 안전성)
2. 4단계: 웹훅 라우팅(`mpay_`), 전체 복구 Sweep, UNKNOWN W 초과 처리, 탈퇴 MEMBERSHIP_PAYMENT Checker
3. 5단계: SYSTEM 환불 PG 실행·정합화
4. 회원 도메인 구독 API 연결, 2a 운영 인덱스·보안 게이트

## 자동 테스트로 재현하지 못한 것(미검증 🟣)

| 항목 | 관리 |
| --- | --- |
| KG이니시스 빌링키 결제 필수 필드(`productCount`·고객 이름·전화·이메일) | E-3 |
| `mpay_` + UUID 37자 결제 ID 허용 여부 | E-3 |
| 거절된 결제의 GET 응답(FAILED vs 404) | E-4 |
| 같은 결제 ID 재POST 응답(409 `ALREADY_PAID` 등) | E-6 |
| 실제 `paidAt` 타임존 표기 | E-3 + KST 변환 테스트 |

## 회원 도메인 협업

- 구독 HTTP API와 `MembershipSubscribeFacade`(C-11 호출·응답 매핑)는 회원 도메인 담당입니다. **바깥 트랜잭션 없이** 호출해 주세요.
- 결제 쪽 테이블에는 `member_id`가 없어 복구가 `MembershipBillingService.findMemberIdOfMembership`(#324)을 씁니다.
