# 멤버십 자동결제 수단 정리 — 교체·폐기 빌링키 원격 삭제와 탈퇴 정산(AUTO_BILLING)

결제 3차 6단계(#391). `REPLACED`·`REVOKED` 빌링키를 PortOne V2에서 원격 삭제(`DELETE /billing-keys/{billingKey}?storeId=…`)하고, 삭제 사실을 조회로 확인한 뒤에만 로컬 키를 `DELETED`로 만든다. 회원 탈퇴의 네 번째 정산 영역 `AUTO_BILLING`을 제공해, 회원의 모든 키가 삭제 확인된 뒤에만 탈퇴가 열린다. 회원 탈퇴 API(`POST /api/members/me/withdraw`)는 회원 도메인이 소유하고 결제의 `WithdrawalPaymentMethodPreparer`(준비)와 `WithdrawalSettlementChecker`(최종 검사)를 부른다.

## 1. 범위와 고정 결정

| 구분 | 결정 |
| --- | --- |
| 교체 카드 | `REPLACED` 키는 그 키를 쓰는 미해결 시도가 없을 때 원격 정리한다 |
| 폐기 카드 | `REVOKED`는 청구 불가 표시일 뿐이다. 이 작업의 원격 삭제 확인과 같지 않으므로 똑같이 정리 대상이다 |
| 탈퇴 준비 | 활성/대기 이용권·진행 주문·멤버십 미정산(C-17)이 있으면 카드 정리 접수 자체를 거절한다 |
| 일반 카드 삭제 API | 추가하지 않는다(MVP 제외 범위 유지) |
| 삭제 시작 | 키를 `DELETE_REQUESTED`로 바꾸고 삭제 요청 행(`BILLING_KEY_DELETION`)을 같은 TX에 저장한다 |
| 원격 호출 | DB 잠금 TX 밖에서만 GET/DELETE. DELETE 응답만으로 완료하지 않고 GET으로 확인한다 |
| 최종 탈퇴 | 모든 키 `DELETED` + 확인 시각 존재 + 기존 전체 정산 검사를 다시 통과해야 한다 |
| 자동 탈퇴 | 하지 않는다. Worker는 결제수단만 정리하고 회원이 탈퇴를 다시 요청한다 |
| 운영 확인 | DELETE는 멈추고 1시간 간격 GET만 계속한다. 강제 완료·표시 해제 API는 없다 |
| 데이터 보존 | 빌링키 행을 물리 삭제하지 않는다(결제 시도 FK·거래 이력 보존) |
| 자동청구 플래그 | `MEMBERSHIP_BILLING_ENABLED`와 별개다. 정리 기능이 신규 자동청구를 켜지 않는다 |

정책값: 키당 삭제 요청 1개(UNIQUE), 실행권 임대 5분, 접수 후 24시간 자동 DELETE 허용(그 뒤 조회 전용 운영 확인), 운영 확인 조회 간격 1시간, 재확인 간격 1·2·4·8분 뒤 10분. 정리 기능과 탈퇴 개방은 별도 기본-OFF 스위치다.

## 2. 두 흐름

### 2-1. 교체·폐기 키 정리 (`BillingKeyDeletionRecoveryFacade`)

```
5분 주기 requestReplacedOnce
  -> 최대 키 ID 고정, ID keyset으로 후보 조회(TX 밖, 삭제 요청 없는 REPLACED·REVOKED·DELETE_REQUESTED 키)
  -> BillingKeyDeletionRequestProcessor.requestReplaced(memberId, keyId)   [TX]
       MEMBER 정산 잠금 -> BILLING_KEY PK 잠금 -> 상태 재검사(재활성화가 먼저면 건너뜀)
       -> 그 키의 미해결 시도(READY·DISPATCHING·UNKNOWN·MANUAL_REVIEW)·회차 강한 차단 검사
       -> DELETE_REQUESTED + billing_key_deletion 저장 -> COMMIT
1분 주기 recoverOnce
  -> 기한(next_check_at)이 된 REQUESTED·REVIEW_REQUIRED 요청 최대 20개(ID 투영, TX 밖)
  -> BillingKeyDeletionExecutionFacade.execute(deletionId)   [NEVER]
       실행권 TX(MEMBER -> KEY -> 요청 잠금, 토큰+1·임대 5분, 미해결 시도 재검사)
       -> GET /billing-keys/{key}?storeId
       -> 이미 삭제 확인(DELETED·검증된 404)이면 결과 TX로 완료
       -> ISSUED이면 식별 확인 기록 TX -> 삭제 직전 재검증 TX(토큰·임대·상태·24시간 창) -> COMMIT -> DELETE
       -> GET 재조회 -> 결과 반영 TX(완료 / 다음 확인 예약 / 운영 확인)
```

`REPLACED` 후보를 읽은 뒤 다른 요청이 먼저 그 키를 `ACTIVE`로 되살리면 잠금 뒤 재검사가 삭제를 건너뛴다. 반대로 삭제 접수가 먼저 커밋되면 기존 등록 서비스가 `DELETE_REQUESTED` 키의 재활성화를 거절한다(`BILLING-006`).

### 2-2. 회원 탈퇴 연결 (회원 도메인 `MemberWithdrawalFacade`, NEVER)

```
MemberWithdrawalFacade.withdraw
  -> 기존 Redis 세션 키 스냅숏
  -> MemberWithdrawalPreparationProcessor.prepare   [TX]
       회원 PK 잠금(첫 DB 작업) -> 비밀번호 재확인 -> 진행 주문·활성/대기 이용권 검사 -> 4 Scope 연결 여부 확인
       -> BillingKeyWithdrawalPreparer.prepare   [MANDATORY]
            C-17 미정산 검사 -> 회원 키 전부(ACTIVE 포함) ID 오름차순으로 정리 접수(PG 호출 없음)
            -> 운영 확인 중 요청 있음 REVIEW_REQUIRED / 삭제 미확인 키 있음 PENDING / 전부 확인 READY
       -> COMMIT (접수는 결과와 무관하게 남는다)
  -> PENDING이면 BILLING-030, REVIEW_REQUIRED이면 BILLING-031 (여기서 던지므로 접수 커밋은 유지)
  -> READY이면 기존 MemberWithdrawalProcessor.withdraw   [별도 TX]
       회원 잠금 -> 비밀번호·주문·멤버십·4 Scope 검사 전부 재수행(AUTO_BILLING = AutoBillingWithdrawalSettlementChecker)
       -> 포인트·장바구니·체험 차단·회원 파기(기존 책임)
  -> 커밋 성공 뒤 기존 Redis 세션 정리
```

준비 단계는 회원 파기·포인트 소멸·장바구니 삭제를 하지 않는다. 카드 정리는 독립적으로 끝나므로, 이후 잔액 불일치 등으로 최종 탈퇴가 거절돼도 이미 삭제한 원격 키는 복구하지 않는다(화면 안내 필요). 두 TX 사이에 새 카드·주문이 생기면 최종 검사가 거절한다. 신규 카드 등록을 무기한 막는 별도 회원 상태는 두지 않는다.

## 3. 상태·멱등·잠금

### 키 상태 (`BillingKey`)

`ACTIVE / REPLACED / REVOKED -> DELETE_REQUESTED -> DELETED`. 기존 `replace / reactivate / revoke`는 그대로이고, `requestDeletion(now)`는 요청 시각을, `confirmRemoteDeletion(at)`는 확인 시각을 최초 값만 남긴다. 삭제 완료 키는 재활성화하지 않는다.

### 삭제 요청 상태 (`BillingKeyDeletion`)

| 현재 | 사건 | 다음 |
| --- | --- | --- |
| REQUESTED | GET/DELETE 일시 장애(5xx·timeout·해석 불가), 24시간 창 안 | REQUESTED + 다음 확인 예약(1·2·4·8·10분) |
| REQUESTED | 401·403(`AUTH_ERROR`), 400(`REQUEST_REJECTED`), 키·상점 불일치(`IDENTITY_MISMATCH`), 실연동 채널(`CHANNEL_NOT_TEST`), 사용 중 시도(`UNRESOLVED_ATTEMPT`), 상태 모순(`STATE_CONFLICT`), 첫 404(`SCOPE_UNPROVEN`), 24시간 만료(`WINDOW_EXPIRED`) | REVIEW_REQUIRED(1시간 간격 GET만) |
| REQUESTED / REVIEW_REQUIRED | 같은 키·같은 상점의 검증된 삭제 확인 | COMPLETED(키 `DELETED` 동시 반영) |
| COMPLETED | 재전달·재실행 | 그대로(SKIPPED) |

- 키당 UNIQUE `uk_billing_key_deletion_key`가 최종 방어다. 생성·상태 변경은 먼저 같은 회원 행으로 직렬화한다.
- 삭제 요청 ID·storeId·reason·requestedAt은 재요청으로 바꾸지 않는다(재접수는 기존 요청 ID를 돌려준다).
- 실행권은 증가 토큰 + 임대 만료 시각을 함께 본다. 임대가 끝난 실행자는 다음 실행자가 토큰을 올리기 전이라도 예약·DELETE 준비를 바꾸지 못한다. 동일 대상의 **검증된 삭제 사실**은 토큰과 무관하게 잠금 아래 반영한다.
- 잠금 순서 `MEMBER(정산 잠금) -> BILLING_KEY(PK 오름차순) -> BILLING_KEY_DELETION`. 결제 회차·시도에는 새 잠금을 추가하지 않는다. 실행기는 ID 투영만 TX 밖에서 읽고, Processor의 첫 DB 작업은 회원 잠금이다.

## 4. PortOne 결과 판정 (`PortOneBillingKeyDeletionClient`, 전용 RestClient 읽기 제한 60초)

| 관측 | 판정 |
| --- | --- |
| 200 `ISSUED` + 요청 키·상점 일치 + 채널 전부 TEST | 식별 확인 기록 후 DELETE 후보 |
| 200 `ISSUED` 인데 채널에 TEST 아닌 것이 있음 | `CHANNEL_NOT_TEST` 운영 확인(실제 청구용 키는 지우지 않는다) |
| 200 `DELETED` + 요청 키·상점 일치 | 완료 근거 |
| 404 + 정확한 `BILLING_KEY_NOT_FOUND` | 과거 같은 요청에서 식별 확인(ISSUED 일치)을 한 적이 있을 때만 완료 근거 |
| 첫 관측이 404이고 식별 확인 이력 없음 | `SCOPE_UNPROVEN` 운영 확인 — 현재 설정의 storeId가 발급 당시 상점이라는 보장이 없다 |
| 다른 키/상점 응답 | `IDENTITY_MISMATCH`, DELETE 금지 |
| 401·403 | `AUTH_ERROR`, DELETE 중단 |
| 400 | `REQUEST_REJECTED`, DELETE 중단 |
| 그 밖의 404·5xx·timeout·해석 불가 | 완료로 보지 않고 다음 확인 예약 |

DELETE 응답은 `DELETE_ACCEPTED`(2xx, 409 `BILLING_KEY_ALREADY_DELETED`, 404 `BILLING_KEY_NOT_FOUND`) / `AUTH_ERROR` / `REQUEST_REJECTED` / `HTTP_ERROR` / `TIMEOUT` / `INVALID_RESPONSE`로만 분류하며 어느 것도 단독으로 완료가 아니다. 이미 원격에서 지워진 예전 키는 운영 확인으로 남을 수 있고, 이런 행은 탈퇴를 자동으로 열지 않는다. 확인되지 않은 상점·키를 추정해 DB를 고치는 절차는 제공하지 않는다.

로그는 내부 `memberId / billingKeyId / deletionId`와 고정 코드만 쓴다. 빌링키·API Secret·URL이 든 예외 원문·PG 원문은 기록하지 않으며 티켓의 `toString()`도 키를 뺀다.

## 5. 복구·설정

삭제 요청은 DB 자체가 복구 기준이다(환불 Redis 큐·Worker enum을 쓰지 않는다). 접수 스캔은 최대 ID를 고정해 keyset으로 끝까지 순회하고, 실행 스캔은 기한 순으로 최대 20개를 각각 독립 TX로 처리한다. 비정상 종료로 남은 임대는 만료 뒤 같은 요청이 재실행된다. 스케줄러는 전용 `billingKeyDeletionTaskScheduler`(스레드 2)로 돌며 테스트 프로필에서는 만들지 않는다.

| 속성 (`application.yml`) | 환경변수 | 기본 | 의미 |
| --- | --- | --- | --- |
| `payment.billing-key-deletion.enabled` | `BILLING_KEY_DELETION_ENABLED` | false | 정리 접수·복구 Worker 활성화 |
| `payment.billing-key-deletion.withdrawal-enabled` | `BILLING_KEY_DELETION_WITHDRAWAL_ENABLED` | false | AUTO_BILLING Scope 제공·탈퇴 준비 연결 |
| `payment.billing-key-deletion.read-timeout` | — | 60s | 삭제 전용 HTTP 읽기 제한 |

두 활성화 값이 모두 true일 때만 `AUTO_BILLING`을 지원한다고 선언한다. 하나라도 false이면 기존처럼 탈퇴는 `MEMBER-008`로 닫혀 있다. 운영 적용 순서·DDL은 `docs/deploy-implementation.md` 10-I.

## 6. 오류 코드

| 코드 | HTTP | 뜻 |
| --- | --- | --- |
| `BILLING-030` | 409 | 자동결제 수단을 정리하는 중 — 정리가 끝난 뒤 탈퇴를 다시 요청 |
| `BILLING-031` | 409 | 자동결제 수단 정리에 운영 확인 필요 |
| `BILLING-032` | 500 | 정리 설정 오류(상점 ID 없음 등) |

기존 코드 그대로: 미정산 `BILLING-004`(`MEMBERSHIP_PAYMENT_UNSETTLED_FOR_WITHDRAWAL`), 영역 미연결 `MEMBER-008`, `DELETE_REQUESTED` 키 재활성화 거절 `BILLING-006`.

## 7. 팀 계약

- **회원(탈퇴 API)**: 신규 계약 `WithdrawalPaymentMethodPreparer`·`WithdrawalPaymentMethodPreparation`(이 PR에 포함)을 회원 잠금 쓰기 TX 안에서 부르는 `MemberWithdrawalPreparationProcessor`와, 준비 결과를 보고 최종 탈퇴를 부르는 `MemberWithdrawalFacade` 변경은 회원 담당이 반영한다. 기존 `MemberWithdrawalProcessor`는 바꾸지 않는다. `withdrawal-enabled`는 그 연동 검증이 끝난 뒤에만 켠다.
- **배포**: 신규 테이블 DDL·환경변수 2개·기본 OFF·배포 전후 스키마 확인(10-I).
- **실제 PG 검증(운영 켜기 전)**: 테스트 상점 시험 키로 GET → DELETE → GET(`DELETED`), 정확한 `BILLING_KEY_NOT_FOUND`의 의미와 상점 범위, 이미 삭제한 키 재요청, DELETE 응답 유실 뒤 GET 복구, 키 삭제 뒤 기존 결제 ID 조회·환불 가능 여부. 살아 있는 시연 회원의 카드로 실험하지 않는다.
