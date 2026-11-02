# 멤버십 이용료 환불 — 즉시 해지·SYSTEM 환불 실행·취소 웹훅·복구·운영 재조회

결제 3차 5단계(#381). 유료 멤버십 즉시 해지의 이용료 환불과, 반영하지 않은 수납(중복·늦은 성공·금액 불일치)의 SYSTEM 환불을 PortOne V2 **금액 지정 취소**로 실행하고, 취소 웹훅·복구 스캔·운영 재조회로 결과를 확정한다. 회원 API(`POST /api/memberships/{membershipId}/terminate`)는 회원 도메인이 소유하고 결제의 `MembershipRefundFacade.requestMemberRefund(memberId, membershipId, idempotencyKey)`를 부른다.

## 1. 불변 조건

| 번호 | 내용 |
| --- | --- |
| F-01 | 환불은 특정 결제 시도(고정 PortOne 결제 ID)에 묶인다. PG 호출의 `paymentId`는 항상 그 시도의 결제 ID |
| F-02 | 환불액·이용일수·수수료·결제기간은 접수 때 저장하고 실행·재조회·재시도에서 다시 계산하지 않는다 |
| F-03 | PG 취소는 `REQUESTED` 커밋 뒤 트랜잭션 밖에서 부른다 |
| F-04 | 같은 회원 요청 키 → 같은 결과(`Idempotency-Key` + 요청 해시, 다르면 `COMMON-011`) |
| F-05 | 결과 불명은 `FAILED`가 아니다. `FAILED`는 식별·금액이 맞는 FAILED 취소 내역에서만 |
| F-06 | SYSTEM 환불 완료는 이용권 종료를 부르지 않는다 |
| F-07 | MEMBER 환불 완료와 이용권 종료는 한 트랜잭션 |
| F-08 | 0원 환불은 PG 없이 접수 트랜잭션에서 완료·종료 |
| F-09 | 환불돼도 결제 시도·회차의 PAID 기록은 유지한다(차단 표시만 남긴다) |
| F-10 | 취소 내역은 금액이 같다는 이유로 연결하지 않는다 — 저장된 취소 ID 정확 일치 → 서버 생성 reason 정확 일치(API 취소) 순서만 |
| F-11 | 강한 차단은 환불 완료·재조회로 해제하지 않는다 |
| F-12 | 탈퇴 회원: 기존 환불의 정산은 계속, 신규 요청은 거절(`MEMBER-001`) |

## 2. 환불 계산

결제기간 `[periodStartAt, periodEndAt)` — 종료 시각은 다음 결제기간이 시작되는 배타적 시각이라 일수에 +1을 붙이지 않는다.

```
periodDays    = DAYS.between(periodStartAt의 날짜, periodEndAt의 날짜)
usedDays      = min(periodDays, DAYS.between(periodStartAt의 날짜, requestedAt의 날짜) + 1)
usedDayFee    = floor(paidAmount × usedDays / periodDays)
penaltyAmount = floor(paidAmount × 20 / 100)
refundAmount  = max(0, paidAmount − usedDayFee − penaltyAmount)
```

| 기간 일수 | 이용일수 | 이용요금 | 위약금 | 환불액 |
| --- | --- | --- | --- | --- |
| 28 | 1 | 139 | 780 | 2,981 |
| 29 | 1 | 134 | 780 | 2,986 |
| 30 | 1 | 130 | 780 | 2,990 |
| 31 | 1 | 125 | 780 | 2,995 |
| 31 | 10 | 1,258 | 780 | 1,862 |
| 30 | 24 | 3,120 | 780 | 0 |

- 첫 결제 회차: `periodStartAt` = PG 결제 시각, `periodEndAt` = 첫 기간 종료(기준일 다음 달 같은 시각). 갱신 회차: 저장된 회차에서 복원한 T·E.
- 시각은 모두 한국 시간 `LocalDateTime`이고 계산기는 시간대를 다시 해석하지 않는다. 정수 나눗셈·`Math.*Exact`만 쓴다.
- 유예기간 요청의 이용일수는 `periodDays`로 제한한다(허용 여부는 접수가 판단).

## 3. 접수 (MEMBER) — `MembershipRefundRequestProcessor.acceptMember`

잠금 순서 MEMBER(활성 회원만) → 이용권 → 회차 PK → 시도 PK 오름차순 → 환불 PK 오름차순. **판정 시각은 잠금을 모두 잡은 뒤 읽는다** — 잠금을 기다리는 사이 자정이나 혜택 종료를 넘겼으면 그 시각으로 본다.

| 단계 | 내용 | 거절 |
| --- | --- | --- |
| 1 | 회원 잠금(탈퇴 → 기존 규칙) | `MEMBER-001` |
| 2 | 같은 키의 기존 행 → 요청 해시 비교 → 기존 결과 반환(새 계산·행·PG 없음). 자격 검사보다 먼저 | `COMMON-011` |
| 3 | 이용권 잠금 → 상태 자격(유료·구독/전환·미종료·활성화됨) | `MEMBERSHIP-008` |
| 4 | 미정산 결제·진행 중 환불(C-17) | `MEMBERSHIP-010` |
| 5 | 정상 반영 PAID 회차 후보 1건(잠금 없는 조회, `periodStartDate DESC, id DESC`) → 회차 PK 잠금 → 시도 전부 잠금 | 후보 없음 → `BILLING-022`(표시할 행 없음, ERROR 로그) |
| 6 | 잠금 뒤 시각으로 자격(활성화 시각 ≤ now, 시작 ≤ now < 유예 종료 또는 종료) | `MEMBERSHIP-008` |
| 7 | 회차에서 복원한 종료 시각 = 이용권 현재 종료, 정상 반영 PAID 시도(PAID·환불 필요 없음·PG 결제 시각 = 회차 결제 시각 µs) 정확히 1개 | 아니면 잠근 PAID 시도에 `REFUND_TARGET_PERIOD_MISMATCH` 차단 + `BILLING-022` |
| 8 | 그 시도의 환불 행: 강한 차단 → `BILLING-022`, REQUESTED → `MEMBERSHIP-010`, COMPLETED → `MEMBERSHIP-008`. FAILED는 막지 않는다(새 날짜로 재계산) | |
| 9 | 계산 → 저장(`paidAmountSnapshot` = 회차 금액, `pgIdempotencyKey = "mrf_v1_" + UUID`, 기간·해시). 0원이면 같은 TX에서 COMPLETED + 이용권 종료 | |

거절은 결과로 돌려주고 Facade가 커밋 뒤 예외로 바꾼다(차단 표시가 함께 커밋되게). 신규 양수 접수는 커밋 뒤 큐 예약 + 실행기 1회 동기 시도 → 최신 행을 짧은 조회 TX로 읽어 응답.

### 응답 `MembershipRefundResult` (회원 Controller가 `data`에 담는다, HTTP 200)

`membershipId, membershipPaymentId, refundId, refundStatus(REQUESTED|COMPLETED|FAILED), paidAmount, periodDays, usedDays, usedDayFee, penaltyAmount, refundAmount, requestedAt, completedAt, membershipTerminated(= COMPLETED), reviewRequired`

## 4. 실행 — `MembershipRefundExecutionFacade.execute(refundId)`

```
실행권 TX(환불 PK만 잠금: REQUESTED·운영 표시 없음·임대 없음/만료·회차 차단 없음·다음 확인 시각 지남(없으면 즉시) → claimToken+1, 임대 5분,
          비어 있는 pgStoreId(서버 설정)·pgRequestReason 고정. 설정 비면 PG 없이 PG_CONFIG_ERROR, PG 키 없는 기존 행은 LEGACY_DISPATCH_UNPROVEN)
→ GET /payments/{paymentId}
→ 결과 반영 TX(TX2, 토큰 불요) …… ①식별 ②누적 취소액 ③소유권 매칭 → 확정/차단/미확정(+POST 허용 여부)
→ POST 허용이면: 재POST 창(최초 발송 + 2h55m 미만) → POST 준비 TX(토큰·임대·REQUESTED·고정값·취소 ID 없음 재검증 + 회차 강한 차단·재POST 창 재확인,
   firstDispatchedAt 최초 1회. 거절되면 POST 없이 임대만 돌려준다)
   → POST /payments/{paymentId}/cancel {storeId, amount=환불액, currentCancellableAmount=원수납액, requester(MEMBER→CUSTOMER / SYSTEM→ADMIN), reason}
     헤더 Idempotency-Key: "<pgIdempotencyKey>" (쌍따옴표 포함, 같은 행은 키·본문 불변)
   → 응답 취소 ID 기록 TX(토큰·REQUESTED·금액 = 환불액·reason = 저장 사유·기존 ID 동일·다른 환불 미사용 검증) → GET → TX2
→ 미확정이면 다음 확인 예약(경과시간 구간: 1분 미만 1분 / 3분 미만 2분 / 7분 미만 4분 / 15분 미만 8분 / 그 이상 10분),
   기준 + 24시간 이상이면 UNKNOWN_WINDOW_EXPIRED 운영 확인. 기준 = firstDispatchedAt, 없으면 requestedAt
```

예약된 다음 확인 시각 전에는 큐 Worker·복구·동기 실행 어느 경로도 실행권을 잡지 않는다(접수 직후 큐와 동기 실행이 겹쳐도 GET·재POST 1회). 다음 확인 예약·자동 중단·임대 해제·발송 준비·응답 기록은 **토큰 일치 + 임대 유효**일 때만 반영한다 — 임대가 끝난 실행자는 다음 실행자가 토큰을 올리기 전에도 아무것도 바꾸지 못한다.

### 취소 응답 분류

| 응답 | 처리 |
| --- | --- |
| 취소 ID 있는 SUCCEEDED·REQUESTED·FAILED | 검증 뒤 ID 기록 → GET → TX2(최종 확정은 GET으로만) |
| timeout·5xx·연결 장애·해석 불가·409 `IDEMPOTENCY_OUTSTANDING_REQUEST` | REQUESTED 유지, 같은 키로 나중에 재확인(창 안에서 재POST) |
| 400 | `PG_REQUEST_REJECTED` 자동 POST 중단 |
| 401·403 | `PG_AUTH_ERROR` 자동 POST 중단 |
| 404, 잔액·상태 409 | GET 재확인, 새 키 금지 |

## 5. 결과 반영 TX2 — `MembershipRefundResultProcessor.apply`

잠금 순서 MEMBER(정산 잠금, 탈퇴 포함) → 이용권 → 회차 PK → 시도 PK 오름차순 → 환불 PK 오름차순(ID 선조회 뒤 한 행씩). 토큰은 요구하지 않는다 — 웹훅·관리자 재조회는 실행자 토큰이 없으므로 확정 결과는 잠금·현재 상태·증거로만 지킨다. 한 관측으로 그 시도의 환불 행 전부를 ID 순으로 평가한다.

대상 환불을 지정한 호출(실행기·관리자 재조회)은 그 한 건의 결과를 돌려주고, 지정 없이 부르는 호출(결제 재조회의 환불 위임·취소 웹훅)은 시도의 환불 전체를 집계한다 — 환불 행이 없으면 외부 취소라 자동 귀속하지 않고 REVIEW_REQUIRED, 여러 행이면 REVIEW_REQUIRED > PENDING > COMPLETED > FAILED 순으로 보수적으로 고른다(옛 FAILED가 새 REQUESTED의 미확정을 가리지 않는다).

| 판정 | 결과 |
| --- | --- |
| ① 식별: 결제 ID·상점(행에 고정된 값, 없으면 서버 설정)·KRW·원수납액(`paidAmountSnapshot`) 모두 일치 | 다음 |
| ① 필수 필드 누락·결제 진행 중 상태 | PENDING(재조회, POST 금지) |
| ① 존재하는 값이 명백히 다름 | 환불 `PG_IDENTITY_MISMATCH` + 시도 `REFUND_EVIDENCE_CONFLICT` |
| ① 내부 PAID인데 PG FAILED | 시도 `PAID_EVIDENCE_CONFLICT` + 환불 `EVIDENCE_CONFLICT` |
| ② 누적 취소액 = SUCCEEDED 취소 totalAmount 합(REQUESTED·FAILED 제외) | 다음 |
| ② 합 불일치·누적액 누락(배열은 있음) | PENDING(강한 차단 아님) |
| ② 알 수 없는 상태·중복 ID·금액 누락 | 불신뢰 관측 → 실행자가 `CANCELLATION_STATUS_UNKNOWN` |
| ③ 저장 ID 정확 일치 / ID 없을 때만 서버 생성 reason 정확 일치 + API 취소 | 금액 = 환불액 검증 → SUCCEEDED: COMPLETED(+MEMBER 종료, 이용권 기간이 대상 회차와 다르면 `TARGET_PERIOD_MISMATCH` 차단) / FAILED: FAILED / REQUESTED: ID 기록·재조회 |
| ③ 같은 reason 복수 ID·다른 환불에 귀속된 취소·FAILED 환불의 취소가 성공으로 관측·매칭 + 설명되지 않는 진행/성공 취소 | 환불 `EVIDENCE_CONFLICT` + 시도 `REFUND_EVIDENCE_CONFLICT` |
| ③ 매칭 없음 + 저장 ID 있음(아직 안 보임) 또는 설명되지 않는 진행/성공 취소 있음 | PENDING, POST 금지 |
| ③ 매칭 없음, 설명 안 되는 취소 없음, PG 키 있음, 운영 표시 없음 | PENDING, **POST 허용** |
| 확정 행(COMPLETED·FAILED) 재전송 | 같은 ID·상태·금액 → 멱등 / 보이지 않음 → 변경 없음 / 다른 상태·금액·같은 reason의 다른 진행/성공 취소 → 증거 충돌(FAILED의 모순 성공을 후속 행에 맞춰 해석하지 않는다) |

## 6. 운영 확인 코드 (`MembershipRefundOpsReason`)

| 구분 | 코드 | 뜻 |
| --- | --- | --- |
| 자동 중단(REQUESTED 유지, 관리자 재조회·검증된 웹훅으로 확정 가능) | `UNKNOWN_WINDOW_EXPIRED` | 24시간 안에 확정 못 함 |
| | `PG_CONFIG_ERROR` | 서버 상점 설정 비어 있음 |
| | `PG_AUTH_ERROR` | 401·403 |
| | `PG_REQUEST_REJECTED` | 400 |
| | `CANCELLATION_STATUS_UNKNOWN` | 취소 내역 해석 불가 |
| | `LEGACY_DISPATCH_UNPROVEN` | PG 키 없는 기존 행(발송 이력 증명 불가) |
| 강한 차단(시도 차단과 함께, 관측만 허용) | `EVIDENCE_CONFLICT` ↔ 시도 `REFUND_EVIDENCE_CONFLICT` | 저장 근거와 PG 모순 |
| | `TARGET_PERIOD_MISMATCH` ↔ 시도 `REFUND_TARGET_PERIOD_MISMATCH` | 대상 회차·시도가 현재 기간과 다름 |
| | `PG_IDENTITY_MISMATCH` ↔ 시도 `REFUND_EVIDENCE_CONFLICT` | 상점·통화·원수납액 불일치 |

정상 확정 시 `leaseExpiresAt`·`nextCheckAt`은 비우고 운영 사유는 이력으로 남는다. 응답의 `reviewRequired` = 강한 차단 또는 (REQUESTED + 자동 중단).

## 7. 취소 웹훅·결제 재조회·복구

- 멤버십 결제 ID의 `Transaction.Cancelled`·`PartialCancelled`·`CancelPending`: 시도 매핑 → 환불 행 없으면 외부 취소로 MANUAL_REVIEW(`EXTERNAL_CANCELLATION_UNEXPLAINED`, GET 없음) → GET 1회 → TX2 → 완료·실패·멱등 PROCESSED / 미확정·조회 실패 RETRY_PENDING / 운영 확인 MANUAL_REVIEW. 웹훅 본문은 믿지 않는다.
- 결제 재조회(실행기의 POST 뒤 GET, 웹훅·복구·관리자 GET)에서 **수납 PAID 시도 + PG 취소 계열(CANCELLED·PARTIAL_CANCELLED)** 이면 결제 상태를 바꾸지 않고 같은 응답을 환불 TX2에 넘긴다(추가 GET·POST 없음). 투영이 아니라 잠금 아래의 최신 시도 상태로 판정한다. 환불 집계 결과를 결제 결과로 그대로 옮긴다 — COMPLETED·FAILED → `REFUND_RECONCILED`(웹훅 PROCESSED) / PENDING → `REFUND_RECONCILIATION_PENDING`(RETRY_PENDING) / REVIEW_REQUIRED·환불 행 없음 → `REFUND_RECONCILIATION_REVIEW_REQUIRED`(MANUAL_REVIEW). 환불이 확인되지 않은 결제 웹훅을 처리 완료로 기록하지 않는다.
- 결제 TX2가 SYSTEM 환불을 요청(또는 재확인)하면 커밋 뒤 큐(`MEMBERSHIP_REFUND_EXECUTE`)에 예약한다. 접수도 같은 큐에 예약한다.
- 복구 스캔(1분): REQUESTED·다음 확인 시각 지남(없음 포함)·임대 없음/만료·운영 표시 없음 → 실행기. 큐 유실을 받는다.
- 환불 실행 기능이 없던 때 `MEMBERSHIP_REFUND_RECONCILIATION_PENDING`으로 운영 확인에 보존된 취소 웹훅은 5분마다 RETRY_PENDING으로 되돌려 큐에 넣는다(다른 MANUAL_REVIEW는 불변).

## 8. 운영 API (SUPER_ADMIN)

| API | 동작 |
| --- | --- |
| `POST /api/admin/membership-refunds/{refundId}/recheck` | 감사(`MEMBERSHIP_REFUND_RECHECK`) 커밋 → GET 1회 → TX2(강한 차단 행은 관측만) → 응답: 저장 상태 + 이번 관측(`pgLookedUp`, `pgPaymentStatus`, 매칭된 취소 ID·상태·금액, 실패 코드). GET이 실패해도 저장 상태를 "조회 성공"처럼 보이게 하지 않는다 |
| `POST /api/admin/membership-refunds/{refundId}/retry` | 접수 TX(첫 DB 작업 회원 잠금): SYSTEM·FAILED·차단 없음·같은 시도에 REQUESTED/COMPLETED 없음·SYSTEM 금액 동일 검증 → 후속 행(`retryOfRefundId`, UNIQUE) 저장 + 같은 TX에서 감사(`MEMBERSHIP_REFUND_RETRY`) → 커밋 → 큐 예약 + 동기 실행 1회. 감사는 **새 후속 행이 생길 때만** 남기고(감사 실패 시 후속 행까지 롤백, PG 호출 없음), 같은 실패 행에 다시 누르면 기존 후속 행을 감사 없이 돌려준다. 조건 불충족 `BILLING-021`(감사 없음) |
| `GET /api/admin/membership-payment-attempts/{attemptId}` | 기존 `systemRefunds` 유지 + `refunds`(MEMBER·SYSTEM 전체, 취소 ID·발송·예약·운영 표시·후속 연결·기간 일수) |

오류 코드: `BILLING-020` 환불 없음, `BILLING-021` 재실행 불가, `BILLING-022` 정합성 차단으로 접수 불가.

## 9. 설정값

| 항목 | 값 |
| --- | --- |
| 실행 임대 | 5분 |
| 재조회 간격 | 경과시간 구간 1·2·4·8·10분 |
| 같은 키 재POST 창 | 최초 발송 + 2시간 55분 미만 |
| 자동 결과 확인 창 | 24시간 |
| 복구 스캔 | 1분(배치 100) |
| 멤버십 취소 POST 읽기 timeout | `portone.membership-refund-read-timeout` 기본 60초(별도 RestClient) |

## 10. 실제 PortOne 테스트 상점에서 별도 확인할 것(자동 테스트 범위 밖)

금액 지정 부분 취소, 동일 Idempotency-Key 재호출의 동일 응답, 취소 내역 `reason` 보존, 부분 취소 웹훅 수신, GET 반영 지연. reason 보존이 확인되지 않아도 금액·시각 매칭 대체 로직은 넣지 않는다.
