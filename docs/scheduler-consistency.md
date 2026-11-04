# 스케줄 작업 정합성 보장 방안

최초 작성일 2026-10-26. 블루그린 겹침 창과 다중 서버에서 스케줄 작업이 두 번 실행돼도 데이터가 틀어지지 않도록 작업별 방어를 정리한다. 공통 선택 기준은 [컨벤션 스케줄러 절](convention.md#스케줄러)을 따르며, 각 담당자가 방식과 검증을 결정한다.

> 갱신: 결제 관련 부분(1절 결론, 2절 표의 4~7·9행, 3절 결제 적용 메모, 4-4~4-7과 4-9, 5절 결제 확인, 6절 결제 테스트, 7절 결제 항목)은 dev `9172f6d`(2026-11-04) 코드 기준으로 고쳤다. 회원 정리 배치·혜택 만료 판정·합의 목록은 `dev` `544bee2`(2026-11-04)에서 대조했다. 결제 검증 수치와 별도 확인 과제는 당시 기록을 유지한다.

## 1. 결론

- 현재 문서의 작업에는 공통 분산락(ShedLock, Redis SET NX)을 추가하지 않았다. 각 도메인이 조건부 삭제·DB 잠금·선점 상태 등으로 중복 실행을 방어한다. 컨벤션은 특정 락을 강제하거나 금지하지 않으며, 필요하면 담당자가 분산락과 작업 자체의 방어를 함께 검토한다.
- 규칙은 방법이 아니라 성질로 정한다. **"스케줄 작업은 여러 인스턴스에서 동시에, 또는 반복해서 실행돼도 결과가 같아야 한다."**
- 방어 수단은 이미 컨벤션에 있는 것만 쓴다. 조건부 상태 전이, 비관적 락 후 상태 재검사, UNIQUE 제약, 외부 호출 전 COMMIT이다.
  - 결제 도메인은 여기에 더해 Redis 지연 큐의 원자적 꺼내기(Lua 스크립트), 선점 임대(lease)와 처리권 번호를 쓴다. 어느 것도 락을 대신하지 않고, 최종 판정은 DB 잠금 아래 상태 재검사가 한다(5절).
- 외부 결제가 나가는 멤버십 자동결제는 PortOne 결제 ID(`mpay_` + UUID)를 시도 행에 저장해 COMMIT한 뒤에만 호출한다. 결과가 불명하면 새 ID로 다시 호출하지 않고 같은 ID로 재조회만 한다. 초안의 "결정적 결제 ID"는 쓰지 않는다(4-6 ③).

## 2. 작업 목록

초안 당시(2026-10-26) 예약 작업 1개를 기준으로 예정 작업을 분류했다. 아래 표는 현재 구현을 반영하며, 회원 정리 배치는 매일 04:10·04:20(KST)에 실행되고 멤버십 혜택 만료는 조회 시점에 판정한다.

결제 행(4~7, 9)은 dev `9172f6d` 코드 기준이다. 이 시점 결제 도메인의 `@Scheduled` 메서드는 11개다. `PaymentScheduledTasks` 2개, `PointExpirationScheduler` 1개, `MembershipBillingScheduledTasks` 6개, `BillingKeyDeletionScheduledTasks` 2개이고, 모두 `@Profile("!test")`라 테스트에서는 만들지 않는다.

| # | 작업 | 도메인(담당) | 상태 | 두 번 실행 시 위험 | 방안 |
|---|---|---|---|---|---|
| 1 | 미확정 자동 장바구니 7일 정리 | recipe | 구현됨 | 없음 | 벌크 DELETE (변경 없음) |
| 2 | TRIAL_BLOCK 만료 기록 삭제 | member(채빈) | 구현됨. 매일 04:10 KST | 재탈퇴 연장과 경합 | 1,000건 조건부 DELETE + 연장 경로 upsert |
| 3 | 감사 로그 1년 경과분 삭제 | member(채빈) | 구현됨. 매일 04:20 KST | 없음 | 1,000건씩 별도 트랜잭션 조건부 DELETE |
| 4 | 결제 5분 만료 | payment(정윤) | 구현됨. Redis 지연 큐 + DB Fallback Sweep | 재고·포인트 이중 복구, 만료 뒤 승인의 돈 미반환 | 커밋 뒤 큐 등록 + 원자적 꺼내기 + 닫기 전 PortOne 재조회 + PAYMENT 잠금 후 상태 재검사 |
| 5 | 포인트 만료 | payment(정윤) | 구현됨. 매일 00:00(KST) 전용 스케줄러 | 잔액 이중 차감 | 회원별 트랜잭션에서 MEMBER_POINT → POINT_EARNING 잠금 + 상태 재검사 |
| 6 | 멤버십 정기결제·첫 유료 결제·갱신 재시도·카드 대기 만료 | payment(정윤), membership(채빈) | 구현됨. 회차·시도 모델, 전용 스케줄러. 신규 청구 플래그 기본 OFF | **이중 결제** | 회원 잠금·재확인 + 회차 UNIQUE + 시도 PK 잠금 선점(임대) + 호출 전 결제 ID 저장·같은 ID 재조회 |
| 7 | 결과 불명 재조회 | payment(정윤) | 구현됨. 웹훅, 지연 큐 재조회, 복구 Sweep, 멤버십 운영자 재조회 API | 없음(PG 조회 중복) | 웹훅과 같은 반영 Processor + 잠금 후 상태 재검사 |
| 8 | 해지 예약·체험 만료 후 일반 전환 | membership(채빈) | 조회 시점 판정 구현. 별도 일반 전환 배치 없음 | 없음 | 활성화·종료·기간/유예 조건으로 혜택 판정. 체험 후 유료 전환 접수는 결제 작업 6에서 처리 |
| 9 | 교체·폐기 빌링키 PortOne 원격 정리 | payment(정윤) | 구현됨. 플래그 기본 OFF | PortOne 삭제 요청 중복 | UNIQUE(billing_key_id) + 회원 잠금 + 임대·처리권 토큰 |

## 3. 공통 규칙

1. **판정은 사용 시점에, 배치는 정리만.** 만료 여부는 체험 자격 검사, 포인트 사용, 멤버십 조회가 각자 `expires_at`, `expires_on`, `end_at`으로 판정한다. 배치가 늦거나 두 번 돌아도 판정 결과가 같다.
2. **대상 조회와 처리를 나눈다.** 대상 ID 목록은 락 없이 조회하고, 한 건마다 별도 트랜잭션에서 잠근 뒤 상태를 다시 검사한다. 이미 처리된 건은 건너뛴다. 한 트랜잭션에 여러 건을 묶지 않아 락 보유 시간을 짧게 하고, 한 건의 실패가 다른 건을 롤백하지 않게 한다.
3. **건별 실패는 ERROR 로그를 남기고 다음 건으로 넘어간다.** 다음 실행에서 다시 대상이 되므로 따로 재시도 큐를 두지 않는다.
4. **업무 경로와 같은 메서드로 상태를 바꾼다.** 만료·재조회 배치는 결제 확정 API·웹훅이 쓰는 전이 메서드를 그대로 호출한다. 이미 "동시에 들어와도 한 번만 반영"을 지키는 메서드이므로 배치 전용 분기를 만들지 않는다.
5. **외부 호출은 선점을 COMMIT한 뒤 락 밖에서 한다.** 흐름은 `시도 선점 → COMMIT → PortOne 호출 → 결과 반영 트랜잭션`이다.
6. **벌크 DELETE·UPDATE는 행별 업무 처리나 이력이 필요 없는 정리에만 쓴다.** UPDATE면 `updated_at`을 함께 갱신한다(`docs/convention.md` 264행).
7. **시각은 주입한 `Clock`과 `zone = "Asia/Seoul"`로 계산한다.** `AutoCartCleanupScheduler`와 같은 방식이다.

**결제 도메인 적용 메모 (dev `9172f6d`)**

- 규칙 1: 포인트 사용은 쓰기 직전 같은 소멸 코드(`PointCommandService.expireStaleLots`)로 만료분을 먼저 소멸시킨다(지연 만료). 멤버십 혜택 만료도 회원 도메인의 시각 판정으로 일어나고, 결제 쪽 정리 작업이 마감 시각에 정확히 돈다고 가정하지 않는다.
- 규칙 2: 대상은 ID만 keyset(마지막으로 본 ID 다음부터)으로 읽는다. OFFSET은 쓰지 않는다. 포인트 만료는 적립 건이 아니라 회원 ID 단위로 읽고 회원마다 커밋한다.
- 규칙 3: 결제 도메인은 재시도에 Redis 지연 큐를 쓴다. 결제 만료·환불 재확인·웹훅 처리는 판단할 수 없으면 큐에 다시 예약하고, 큐 예약이 사라져도 DB 상태를 보고 Fallback Sweep이 다시 넣는다. 원본은 항상 MySQL이다. 예상한 업무 거절·외부 실패는 WARN, 예상하지 못한 오류는 ERROR와 스택으로 구분한다. 빌링키 삭제·멤버십 스캔의 오류 스택은 예외 타입·호출 위치만 보존하고 메시지·원인 메시지의 비밀값은 제거한다.
- 규칙 4: 결제 만료가 PG `PAID`·`FAILED`를 보면 확정 API·웹훅과 같은 `PaymentConfirmProcessor`로 판정한다. 멤버십 결과 반영은 자기 호출 뒤 조회·복구 Sweep·웹훅·운영자 재조회가 모두 같은 `MembershipChargeResultProcessor`를 거친다.
- 규칙 5: 멤버십 자동결제의 실제 흐름은 `접수(READY 시도와 결제 ID 저장) → COMMIT → 선점 TX(READY → DISPATCHING) → COMMIT → PortOne POST → 같은 결제 ID GET → 결과 반영 TX`다. 진입점은 `Propagation.NEVER`로 바깥 트랜잭션 안에서 호출되면 PG 요청 전에 거절한다.

## 4. 작업별 방안

### 4-1. 미확정 자동 장바구니 정리 (구현됨)

- `AutoCartRequestRepository.deleteByStatusAndCreatedAtBefore`가 `status = PREVIEWED AND created_at < :before`를 한 문장으로 지운다.
- 두 번째 실행은 0건을 지운다. 확정과 겹쳐도 확정된 행은 `CONFIRMED`라 조건에서 빠진다.
- **변경 없음.**

### 4-2. TRIAL_BLOCK 만료 기록 삭제

- `TrialBlockCleanupScheduler`는 매일 04:10(KST)에 `expires_at <= :now`를 최대 1,000건씩 삭제한다. 각 묶음은 별도 트랜잭션이고 삭제 수가 1,000이면 반복한다. 중복 실행은 조건부 삭제로 방어한다.
- 위험은 중복 실행이 아니라 **재탈퇴 연장과의 경합**이다. 만료된 기록을 탈퇴 경로가 읽고 연장하려는 사이에 배치가 지우면, 연장 UPDATE가 0건이 되어 차단 기록이 사라진다.
- 탈퇴 경로의 기록은 `phone_hash` UNIQUE 기준 upsert(`INSERT ... ON DUPLICATE KEY UPDATE expires_at = ...`)로 한다. 행이 지워졌으면 새로 만들어지고, 남아 있으면 연장된다.
- `TrialBlockService.recordWithdrawal`은 활성화 이력이 있을 때만 upsert를 호출하고 `GREATEST(expires_at, :expires)`로 기존 기간을 줄이지 않는다. 이력 없는 재탈퇴는 upsert를 호출하지 않아 기존 만료가 유지된다.
- 체험 자격 검사는 행의 존재가 아니라 `expires_at > now`로 판정한다(공통 규칙 1).

### 4-3. 감사 로그 1년 경과분 삭제

- `AdminAuditCleanupScheduler`는 매일 04:20(KST)에 실행하며 `created_at <= now - 1년` 조건으로 삭제한다. 감사 로그는 추가만 하므로 만료 삭제는 중복 실행돼도 같은 보관 정책을 유지한다.
- 한 번에 많이 지우면 긴 트랜잭션과 큰 undo가 생기므로 네이티브 `DELETE ... LIMIT 1000`을 0건이 될 때까지 반복한다. JPQL은 LIMIT을 지원하지 않는다.

### 4-4. 결제 5분 만료 (구현됨)

방식은 Redis 지연 큐 + DB Fallback Sweep이다. MySQL이 원본이고 Redis는 실행 신호일 뿐이라, 큐에서 사라진 작업은 DB 상태로 다시 등록한다.

**등록: 커밋 뒤에만**
- 주문 생성 트랜잭션이 커밋되면 `PaymentExpirationScheduleListener`(`@TransactionalEventListener(phase = AFTER_COMMIT)`)가 `PAYMENT_EXPIRE:{paymentId}`를 결제 만료 시각(`expires_at`, Asia/Seoul)으로 등록한다. 롤백된 결제의 작업은 남지 않는다.
- 큐는 Redis Sorted Set `payment:delay-queue` 하나다(`PaymentDelayQueue` → `infra/redis/RedisDelayQueue`). member가 작업(`종류:ID`), score가 실행 시각(epoch millis)이다.
- 신규 등록은 Lua 스크립트의 `ZADD LT`(`enqueueIfEarlier`)라 이미 있는 작업은 더 이른 시각으로만 바뀐다. 재조회 예약은 `reschedule`(일반 ZADD)로 시각을 뒤로 민다.
- Redis 등록이 실패하면 ERROR 로그만 남긴다. 주문·결제는 이미 커밋됐고, 결제가 `IN_PROGRESS`로 남아 Fallback이 다시 등록한다.

**소비: 원자적 꺼내기**
- `PaymentScheduledTasks.pollDelayQueue`(`@Scheduled(fixedDelay = 1000)`, 기본 스케줄러)가 `PaymentDelayQueueWorker.poll()`을 부른다.
- `popDue`는 `ZRANGEBYSCORE` + `ZREM`을 한 Lua 스크립트로 실행해 실행 시각이 지난 작업을 최대 20건 꺼내면서 지운다. 여러 인스턴스가 함께 돌아도 같은 작업을 둘이 꺼내지 않는다.
- Worker는 작업 종류별 Facade로 넘기기만 한다. `PAYMENT_EXPIRE`는 `PaymentExpirationFacade.execute`로 간다. 같은 큐에 웹훅 처리·회원 환불 재확인·SYSTEM 환불 실행·마트 거절 환불 실행·멤버십 환불 실행도 들어 있다(4-7). 한 작업의 예외는 같은 배치의 다른 작업을 막지 않는다.

**복구: Fallback Sweep**
- `PaymentScheduledTasks.sweepFallback`(1시간마다, 기동 1분 뒤 첫 실행)이 `PaymentFallbackScheduler.sweep()`을 부른다.
- 만료 쪽 대상은 `status = IN_PROGRESS AND expires_at IS NOT NULL AND expires_at <= now - 10분`인 결제 ID다(`PaymentRepository.findStaleInProgressPaymentIds`, ID keyset 100건). 10분은 만료 Facade가 1분 간격으로 다시 보는 구간(`RECHECK_WINDOW`)이라 그 안의 결제와 겹치지 않는다.
- Sweep은 처리하지 않는다. PortOne도 Processor도 부르지 않고 지금 시각으로 큐에 넣기만 해서 처리 경로를 Worker 하나로 유지한다.

**판정: 닫기 전에 PortOne 재조회**
- `PaymentExpirationFacade`는 트랜잭션을 걸지 않는다. 스냅샷 조회(짧은 readOnly) → PortOne 조회(락 없음) → Processor(PAYMENT 잠금) 순이다. 상태를 직접 바꾸지 않는다.
- 스냅샷 단계: 결제가 없거나 `IN_PROGRESS`가 아니면 `SKIPPED`, 만료 시각이 없으면 `MANUAL_REVIEW`, 만료 시각 전이면 만료 시각으로 다시 예약한다.
- PortOne 조회 결과별 처리는 아래와 같다.

| PortOne 결과 | 처리 | 결과 |
|---|---|---|
| 404(`PAYMENT_NOT_FOUND`), `READY` | `PaymentExpirationProcessor.expire` | `EXPIRED` |
| `PAID`, `FAILED` | 확정 API·웹훅과 같은 `PaymentConfirmProcessor.process` 후 `SystemRefundExecutionFacade.scheduleAfterConfirm` | 판정별(아래) |
| `PENDING`, `VIRTUAL_ACCOUNT_ISSUED`, `UNKNOWN`, 5xx·timeout·연결 실패 | 1분 뒤 재조회 | `RESCHEDULED`, 만료 시각 + 10분이 지나면 `STALLED` |
| `CANCELED`, `PARTIAL_CANCELED` | 자동 반영 안 함 | `MANUAL_REVIEW` |
| 그 밖의 조회 오류 코드, 내부 오류 | 자동 반영 안 함 | `MANUAL_REVIEW` |

- `PAID`·`FAILED`의 확정 판정(`PaymentApprovalOutcome`)은 아래와 같다.
  - `APPROVED`: PG 결제 완료 시각(`paidAt`)이 만료 시각 이내면 Worker가 늦게 확인해도 정상 승인한다(서버 시각이 아니라 PG 시각으로 판정, `PaymentVerifier`).
  - `EXPIRED`(만료 뒤 승인): 결제 `FAILED(EXPIRED)`, 주문 `PAYMENT_TIMEOUT` 종료, `SYSTEM` 환불을 만들고 커밋 뒤 `SYSTEM_REFUND_EXECUTE`를 예약한다.
  - `ALREADY_CLOSED`: 만료 큐가 먼저 `FAILED`로 닫은 뒤 PG 승인이 확인된 경우다. 결제는 `FAILED` 유지, 주문·포인트·재고는 건드리지 않고 `SYSTEM` 환불로 돈만 돌려준다.
  - `AMOUNT_MISMATCH`: `SYSTEM` 환불 실행을 예약한다.
  - `CURRENCY_MISMATCH`: KRW가 아닌 결제다. 결제·주문·포인트·재고를 그대로 두고 `SYSTEM` 환불도 만들지 않는다. 만료 결과는 `MANUAL_REVIEW`이고 재예약하지 않는다.
  - `ORDER_MISMATCH`: `MANUAL_REVIEW`. `NOT_PAID`(금액·완료 시각 누락): 1분 뒤 재조회. `INVALID_STATUS`: `SKIPPED`.
- `STALLED`·`MANUAL_REVIEW`는 영구 종료가 아니다. 결제가 `IN_PROGRESS`로 남는 한 Fallback이 1시간마다 다시 넣고, Worker가 PortOne 조회부터 다시 한다(`PaymentExpirationOutcome`).

**만료 반영: 잠금 후 재검사**
- `PaymentExpirationProcessor.expire`는 `@Transactional`에서 기존 잠금 순서 `PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고`로 잠그고, `IN_PROGRESS`일 때만 바꾼다. 현재 시각이 만료 시각 전이면 닫지 않는다(마지막 방어).
- 같은 트랜잭션에서 결제 `FAILED(EXPIRED)`, 주문 `CANCELED(PAYMENT_TIMEOUT)` 종료(`OrderFacade.closeByPaymentFailure`), 사용 포인트 복원·적립 예정 취소(`restoreUsageAndCancelEarning`), 재고 복구를 한다.
- 이미 종료된 주문이면 `closeByPaymentFailure`가 빈 목록을 돌려줘 재고를 다시 복구하지 않는다. 포인트도 이미 되돌렸으면 바꾸지 않는다.
- 확정 API·웹훅과 같은 PAYMENT 잠금을 잡으므로 먼저 잡은 쪽만 상태를 바꾼다. 두 번째 실행이나 웹훅과 겹친 실행은 상태 재검사에서 `SKIPPED`가 된다.
- 만료 후 늦은 승인은 결제 도메인이 시스템 환불만 처리하고 주문·재고는 다시 바꾸지 않는다(요구사항 182행, 233~234행). 초안과 달리 만료 처리는 닫기 전에 PortOne을 조회하고, 늦은 승인을 확인하면 `SYSTEM` 환불 실행까지 잇는다.

### 4-5. 포인트 만료 (구현됨)

- `PointExpirationScheduler.expire()`가 `@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul", scheduler = "pointExpirationTaskScheduler")`로 매일 00:00에 돈다. 기준 날짜는 실행 시작 때 `LocalDate.now(clock)`으로 한 번 정해 배치 전체에 같은 날짜를 쓴다.
- 잔액·상태·이력을 함께 바꾸므로 벌크 UPDATE를 쓰지 않는다(`docs/convention.md` 271행).
- 대상은 POINT_EARNING ID가 아니라 **회원 ID** 목록이다. `status = COMPLETED AND remaining_amount > 0 AND expires_on < :today AND member_id > :afterMemberId`인 회원 ID를 회원 ID 순으로 100건씩 잠그지 않고 읽는다(`PointEarningRepository.findExpirationCandidateMemberIds`). `expires_on` 당일까지 쓸 수 있으므로 `<`다.
- 회원마다 `PointCommandService.expireMember(memberId, today)`가 새 트랜잭션을 연다. `MEMBER_POINT`(없으면 먼저 만든 뒤)를 잠그고, 만료 대상 적립 건을 `PESSIMISTIC_WRITE`로 id 오름차순 잠근다(`findExpiredForUpdate`). 건마다 `PointEarning.expire()`가 `COMPLETED`일 때만 남은 양을 돌려주며 `EXPIRED`로 바꾸고, 서비스가 `balance`를 그만큼 줄이고 POINT_HISTORY `EXPIRED`를 남긴다. 이미 `EXPIRED`·`FORFEITED`면 0이라 아무것도 바꾸지 않는다.
- 두 번째 실행이나 다른 서버의 동시 실행은 `MEMBER_POINT`에서 기다린 뒤 소멸할 대상이 없어 0을 돌려주므로 잔액이 두 번 차감되지 않는다. 분산락은 없다.
- 한 회원이 실패하면 그 회원만 롤백하고 ERROR 로그 후 다음 회원을 계속한다. 실패한 회원은 다음 날 배치나 그 회원의 다음 포인트 사용(지연 만료)에서 처리된다. 대상 회원 조회 자체가 실패하면 이번 실행을 끝낸다.
- 주문 생성의 사용 예약과 취소 시 복원도 같은 행을 잠그므로 경합이 직렬화된다. 복원 시점에 원래 적립 건의 만료일이 지났으면 복원 경로가 그 적립 건의 남은 양을 먼저 소멸시키고, 환불된 사용분은 `RESTORED` 이력 뒤 잔액에 넣지 않고 바로 `EXPIRED` 이력으로 소멸시킨다. 배치 대상이 되지 않는다(요구사항 462행). 원래 적립 건이 탈퇴로 소멸(`FORFEITED`)했으면 `RESTORED`와 탈퇴 소멸 이력만 남긴다.
- 포인트 사용은 배치 실행 여부와 상관없이 쓰기 전에 같은 소멸 코드로 만료분을 먼저 소멸시키고, `expires_on >= 오늘`인 적립 건만 쓴다(공통 규칙 1).
- 실행 스레드: `PointSchedulingConfig`(`@Profile("!test")`)가 포인트 전용 `pointExpirationTaskScheduler`(스레드 1개, `point-expiration-`)를 만든다. 스케줄러 빈을 직접 등록하면 Spring Boot가 기본 `taskScheduler`와 `applicationTaskExecutor`를 만들지 않으므로, 두 빈도 Boot와 같은 빌더·이름으로 함께 등록한다(`applicationTaskExecutor`는 `@Lazy`). 그래서 기본 스케줄러(스레드 1개)의 결제 지연 큐 1초 폴링이 포인트 배치에 막히지 않는다.

### 4-6. 멤버십 정기결제·첫 유료 결제·갱신 재시도·카드 대기 만료 (구현됨)

실제 돈이 나가는 유일한 배치라 여러 겹으로 막는다. 구현은 회차와 시도를 나눈다.

- 회차 `MEMBERSHIP_PAYMENT`: 이용기간 하나의 청구. 상태 `PENDING`, `RETRY_WAITING`, `PAYMENT_METHOD_REQUIRED`, `UNKNOWN`, `MANUAL_REVIEW`, `PAID`, `FAILED`. `PAID`·`FAILED`는 확정이라 바뀌지 않는다.
- 시도 `MEMBERSHIP_PAYMENT_ATTEMPT`(`MembershipPaymentAttempt`): PortOne 결제 ID 하나에 대한 호출 단위. 상태 `READY`, `DISPATCHING`, `UNKNOWN`, `MANUAL_REVIEW`, `PAID`, `FAILED`, `CANCELED`. 빌링키는 접수 때 고정된다.
- 초안의 `attempt_count`·`last_attempt_at`·`portone_payment_id`를 회차에서 올리는 조건부 UPDATE 방식은 쓰지 않는다. 회차의 이 컬럼들은 매핑에서 빠졌다.

**스케줄러**

`MembershipBillingScheduledTasks`(`@Profile("!test")`)가 전용 스케줄러 `membershipBillingTaskScheduler`(스레드 2개, `MembershipBillingSchedulingConfig`)에서 돈다. PG 호출로 수십 초가 걸릴 수 있어 기본 스케줄러와 포인트 스케줄러를 막지 않게 분리했다. 신규 청구 플래그는 `recipick.membership-billing.enabled`(`MEMBERSHIP_BILLING_ENABLED`, 기본 `false`)다.

| 메서드 | 호출 | 주기(첫 실행) | 플래그 |
|---|---|---|---|
| `convertTrials` | `TrialConversionFacade.runOnce` (체험 → 첫 유료 결제 접수·실행) | 5분(2분) | OFF면 0 |
| `renewMemberships` | `MembershipRenewalFacade.runOnce` (월 갱신 접수·재시도·카드 대기 재개) | 5분(2분 30초) | OFF면 0 |
| `recoverCharges` | `MembershipChargeRecoveryFacade.recoverOnce` (오래된 선점·결과 불명 재조회, READY 임대 만료) | 5분(3분) | 무관 |
| `settleRenewals` | `MembershipRenewalFacade.settleOnce` (마감 지난 회차·해지된 대기 회차 정리, PG 호출 없음) | 5분(3분 30초) | 무관 |
| `recoverRefunds` | `MembershipRefundRecoveryFacade.recoverOnce` (접수된 환불 실행·재조회) | 1분(1분 30초) | 무관 |
| `reopenPendingCancelWebhooks` | `MembershipRefundRecoveryFacade.reopenPendingCancelWebhooks` | 5분(4분) | 무관 |

**① 회차 생성: 회원 잠금 + 재확인 + UNIQUE**
- 체험 전환은 `TrialConversionProcessor.accept`가 회원마다 `MEMBER → 체험 이용권 PK` 순서로 잠그고 전환 조건을 다시 확인한 뒤 회차와 READY 시도를 만든다. 카드가 없다고 정상 확인된 경우만 전환을 종료하고, 빌링키 조회 예외는 롤백 후 다음 실행에서 다시 본다.
- 갱신은 `MembershipRenewalProcessor.accept`가 `MEMBER → 이용권 → 회차 PK → 시도 PK(오름차순)`로 잠근 뒤 시각을 읽는다. 이번 기간 회차가 있으면 이어 가고, 없을 때만 새 회차를 만든다.
- 최종 방어는 UNIQUE다. 회차 `UNIQUE(membership_id, period_start_date)`, 바로 구독 회차 `UNIQUE(idempotency_key)`, 시도 `UNIQUE(membership_payment_id, attempt_no)`와 `UNIQUE(portone_payment_id)`.

**② 시도 선점: 시도 PK 잠금 + 상태 재검사 + 임대**
- 접수 TX1이 READY 시도를 결제 ID와 함께 저장하고 COMMIT한다. READY는 "PortOne에 보낸 적 없음"을 보장하는 상태다. 임대 만료는 `min(now + L, 마감)`이다(L 10분, 마감은 첫 결제 접수 유효 창의 끝 또는 갱신 결제 마감 G).
- 실행기 `MembershipChargeExecutor.execute`가 선점 TX `MembershipChargeDispatchProcessor.claim`을 연다. 시도 PK 한 행만 잠그고, READY이고 임대 안일 때만 `DISPATCHING`과 `dispatched_at`을 기록한다. 여러 서버가 같은 READY를 집어도 잠금 뒤 상태 검사로 하나만 성공한다.
- 선점에 진 쪽이 잠금 아래에서 본 시도가 임대 안의 `DISPATCHING`이면 다른 Worker가 PG 호출 중이므로 GET·POST 없이 `IN_PROGRESS`로 끝낸다.
- POST 직전에 플래그·접수 유효 창·회차 차단을 다시 본다. 걸리면 POST를 보내지 않고 같은 선점일 때만 READY로 되돌린다(`releaseUnsent`).

**③ PortOne 결제 ID: 시도마다 새 ID, 호출 전에 저장**
- 결제 ID는 결정적 값(`mp-{id}`)이 아니라 시도 접수 때 만드는 `mpay_` + 하이픈 없는 UUID(37자)다(`MembershipPaymentAttempt.newPaymentId`).
- ID가 READY 저장 때 DB에 남으므로 선점 후 앱이 죽어 `DISPATCHING`으로 멈춘 시도도 같은 ID로 PortOne에서 결과를 조회할 수 있다.
- PG에 갔을 수 있는 시도(`DISPATCHING`·`UNKNOWN`·`MANUAL_REVIEW`)가 남아 있으면 새 ID를 만들지 않고 회차도 닫지 않는다. 새 시도는 앞 시도가 확정 `FAILED`이거나 미발송으로 접힌 `CANCELED`일 때만 만든다. 어떤 경우에도 새 결제 ID로 다시 POST하지 않는다.
- 초안의 "PortOne V2가 실패한 결제 ID로 재시도를 허용하는가"는 시도마다 새 ID를 쓰는 방식으로 정했다. 같은 ID 재POST 응답과 `mpay_` ID 허용 여부는 PortOne 실험(E-3, E-6)으로 확인할 항목이다(7절).

**결과 반영: TX2**
- 자기 POST 뒤의 GET, 복구 Sweep, 웹훅, 운영자 재조회가 모두 `MembershipChargeResultProcessor`(TX2)로 반영한다. 확정은 POST 응답이 아니라 같은 결제 ID의 GET 결과로만 한다.
- 잠금 순서는 `MEMBER → 이용권 → 회차 PK → 그 회차의 시도 전부(PK 오름차순)`이고 판정 시각은 잠금을 모두 잡은 뒤 읽는다. 결과 기록과 이용권 반영을 한 트랜잭션에서 커밋한다.
- 확정 시도·확정 회차는 바꾸지 않는다. 같은 결과의 재전송은 저장된 근거와 대조해 멱등, 수납 정정, 강한 차단 중 하나로 끝난다. 회차에 강한 정합성 차단 시도가 하나라도 있으면 어떤 결과도 반영하지 않는다.
- 이용권에 반영하지 않는 수납(마감 뒤 성공, 탈퇴 뒤 수납, 늦게 확인된 수납 등)은 `SYSTEM` 환불을 요청하고, 커밋 뒤 큐에 환불 실행을 예약한다.
- 결제와 멤버십을 함께 바꾸는 조립은 Facade·Processor가 한다. 회원 도메인 반영이 예외로 끝나면 결제 상태 기록도 함께 롤백되고 복구가 같은 결제 ID로 다시 한다.

**재시도·카드 대기·마감**
- 첫 결제: 접수 유효 창 30분(회차 첫 시도 `requested_at` 기준, 재발급해도 다시 시작하지 않는다), 회차당 시도 3개.
- 갱신: 확정 실패 뒤 24시간(R)마다, 회차당 PG 발송 3회(N)까지, 결제 마감 G = 직전 이용 종료 T + 3일. R·N은 정책값이라 설정이 다르면 기동이 멈춘다(`MembershipBillingProperties`).
- 발송 횟수는 `dispatched_at`이 있는 시도 수로 센다. 미발송으로 되돌린 시도와 미발송 `CANCELED`는 빠지므로 중복 실행으로 횟수가 어긋나지 않는다.
- 카드 대기(`PAYMENT_METHOD_REQUIRED`)는 활성 빌링키가 없는 갱신 회차다. 카드 등록이 커밋되면 `MembershipRenewalFacade.resumeForMember`가 그 회원의 대기 회차를 재개하고, 앞선 실패의 24시간 간격은 앞당기지 않는다.
- 종료 조건은 해지·종료 → 마감 G → 발송 한도 순으로 본다. 걸리면 회차 `FAILED`와 자동갱신 끔이다. 초안의 "3일 만료 조건부 전이" 대신 `settleOnce`가 같은 잠금 순서로 정리한다(PG 호출·새 시도 없음). 마감까지 회차가 한 번도 없던 이용권은 `settleOverdue`가 `FAILED` 회차 1건으로 닫는다(소급 청구 없음).

**결과 불명 창**
- 복구 Sweep 기준: `DISPATCHING`은 `dispatched_at + L(10분) + M(2분)`이 지나면, `UNKNOWN`은 마지막 갱신 후 M(2분)이 지나면 같은 결제 ID로 재조회한다. READY 임대 만료는 `handleExpiredReady`가 `CANCELED`로 접은 뒤 새 시도를 만들지 회차를 닫을지 정한다.
- 결과 불명 창 W(24시간)는 `dispatched_at` 하나만 기준으로 한다. 지나면 그 GET이 마지막 자동 조회이고, 시도 `MANUAL_REVIEW`(`UNKNOWN_WINDOW_EXPIRED`), 회차가 `PENDING`·`UNKNOWN`이면 `MANUAL_REVIEW`, 운영 확인 표시를 남긴다. 차단은 아니라 웹훅·운영자 재조회는 계속 받는다.
- 초안의 "일정 시간이 지난 `IN_PROGRESS`는 `UNKNOWN`으로 보고 재조회"는 위의 오래된 `DISPATCHING` 재조회로 구현됐다.

### 4-7. 결과 불명 재조회 (구현됨)

- 대상(현재 코드): 상품 결제의 웹훅 이벤트, 회원 취소 환불 `REQUESTED`(결제 `CANCEL_REQUESTED`), `SYSTEM` 환불 `REQUESTED`, 마트 거절(ADMIN) 환불 `REQUESTED`, 만료 확인 중인 결제 `IN_PROGRESS`(4-4), 멤버십 시도 `DISPATCHING`(오래된 것)·`UNKNOWN`(4-6), MEMBERSHIP_REFUND `REQUESTED`.
- PortOne 조회는 트랜잭션 밖에서 한다. 반영은 웹훅이 쓰는 전이 메서드(Processor·TX2)로 하고, 그 메서드가 잠금과 상태 재검사를 한다(공통 규칙 4).
- 중복 실행이나 웹훅과 겹친 실행은 PortOne 조회가 한 번 더 일어날 뿐이고, 두 번째 반영은 상태 검사에서 걸러진다.

**웹훅**
- `POST /api/webhooks/portone` → `PortOneWebhookFacade.receive`는 검증 → 저장 → 큐 등록(`WEBHOOK_PROCESS`) → 200까지만 한다. `webhook_id` UNIQUE 충돌은 이미 받은 웹훅으로 보고 200이다.
- Worker가 `PaymentReconciliationFacade.processWebhook`을 부른다. 흐름은 `TX-A(claim, 처리권 번호 증가) → PortOne 재조회 → TX-B(기존 Processor) → TX-C(결과 기록)`이고, TX-C는 claim 때 받은 처리권 번호가 그대로일 때만 기록한다. 웹훅 본문의 상태는 믿지 않는다.
- 재시도 간격 1분 → 2분 → 4분 → 8분, 5회까지 잡아도 끝내지 못하면 `MANUAL_REVIEW`. `PROCESSING`이 10분을 넘으면 Fallback이 다시 넣는다.
- 멤버십 결제 ID(`mpay_`)는 `MembershipWebhookReconciliationFacade`로 분기해 같은 결제 ID 재조회(`MembershipChargeExecutor.reconcile`)로 넘긴다. 취소 계열은 멤버십 환불 정합화로 넘긴다.

**상품 환불**
- 회원 환불: `RefundReconciliationFacade.reconcile`가 요청 5분 뒤부터 10분 간격으로 요청 후 3시간까지 다시 묻는다. 취소 POST는 하지 않고 이미 시작된 취소의 상태만 본다. 반영은 `PaymentCancelProcessor.apply`(PAYMENT 잠금).
- `SYSTEM` 환불·마트 거절 환불: `SystemRefundExecutionFacade`·`AdminRefundExecutionFacade`가 취소 POST 전에 항상 GET으로 이미 접수된 취소를 확인한다. 환불 행마다 고정된 멱등 키(`recipick-refund-` 접두)를 써서 동시에 요청돼도 PortOne에서 한 건이다. 재실행 간격 10분, 고빈도 구간 3시간.
- 고빈도 구간이 끝난 건은 Fallback Sweep이 1시간마다 다시 넣는다(4-4와 같은 Sweep).
- 상품 결제에는 운영자 재조회 API가 없다. `/api/admin/payments`는 조회(GET)만 있다.

**멤버십 결제·환불**
- 멤버십 결제 시도: 복구 Sweep(4-6), 웹훅, 운영자 재조회 `POST /api/admin/membership-payment-attempts/{attemptId}/recheck`(`MembershipChargeExecutor.recheckForAdmin`). 운영자 재조회는 차단된 회차도 GET은 하되 TX2는 부르지 않고 관측값만 돌려준다.
- 멤버십 환불: `MembershipRefundRecoveryFacade.recoverOnce`(1분)가 `REQUESTED`이고 다음 확인 시각이 지났고 임대가 없거나 끝났고 운영 확인 표시가 없는 행을 집어 `MembershipRefundExecutionFacade.execute`로 넘긴다. Redis 큐 예약(`MEMBERSHIP_REFUND_EXECUTE`)이 유실돼도 이 스캔이 받는다. 결과 확인 창 24시간, 같은 키 재POST는 최초 발송 + 2시간 55분 미만에서만 한다.
- 운영자 API: `POST /api/admin/membership-refunds/{refundId}/recheck`(GET 1회, POST 없음), `POST /api/admin/membership-refunds/{refundId}/retry`(FAILED `SYSTEM` 환불의 후속 환불 접수).

### 4-8. 해지 예약·체험 만료 후 일반 전환

- 구현은 조회 시점 판정이다. 활성화 시각(`activated_at`)·이용 시작·종료·현재 혜택 종료(`COALESCE(grace_end_at, end_at)`)로 판정하며, 과거 시점 조회는 종료 시각도 기준 시각과 비교한다. 별도 일반 전환 배치는 없다.
- `MembershipService`·`MembershipRepository`가 내 멤버십·혜택·주문 시점 유형을 같은 기간 규칙으로 조회한다. 해지 예약 시 유예를 제거하므로 유예 중 예약하면 즉시 혜택이 종료된다.
- 체험 종료 시 첫 결제 접수와 갱신 상태 정리는 4-6의 결제 작업이다. 조회 결과만으로 청구나 이용권 변경을 일으키지 않는다.

### 4-9. 교체·폐기 빌링키 PortOne 원격 정리 (구현됨)

- `BillingKeyDeletionScheduledTasks`(`@Profile("!test")`)가 전용 스케줄러 `billingKeyDeletionTaskScheduler`(스레드 2개)에서 돈다.
  - `requestReplacedKeys`: 5분마다(첫 실행 30초) `BillingKeyDeletionRecoveryFacade.requestReplacedOnce`. `REPLACED`·`REVOKED`·`DELETE_REQUESTED` 키를 상한 ID를 고정한 keyset(100건)으로 읽어 정리 요청을 접수한다.
  - `recoverDeletions`: 1분마다(첫 실행 30초) `recoverOnce`. `REQUESTED`·`REVIEW_REQUIRED`이고 다음 확인 시각이 된 요청을 최대 20건 `BillingKeyDeletionExecutionFacade.execute`로 실행한다.
- 플래그 `payment.billing-key-deletion.enabled`(`BILLING_KEY_DELETION_ENABLED`, 기본 `false`)가 꺼져 있으면 두 작업 모두 0이다.
- 중복 방어: `billing_key_deletion`의 `UNIQUE(billing_key_id)`로 키당 요청 하나, 쓰기 경로의 잠금 순서 `MEMBER → BILLING_KEY → BILLING_KEY_DELETION`, 실행 전 `claim`(완료 아님·확인 시각 도달·임대 없음일 때만 처리권 토큰 증가 + 5분 임대). 실행 실패는 임대가 끝난 뒤 같은 DB 요청을 다시 집고 새 요청·새 키를 만들지 않는다.

## 5. 분산락을 쓰지 않는 이유와 재검토 조건

- 정합성을 지키는 수단은 4절의 행 단위 방어다. 분산락은 이를 대체하지 못한다. 락을 잡은 인스턴스가 TTL보다 오래 걸리거나 GC로 멈추면 다른 인스턴스가 같이 실행된다.
- 4절만으로 중복 실행의 비용은 "대상 조회 한 번 더, 건너뛰기"뿐이다.
- 재검토 조건: 대상 조회 자체가 무거워 두 번 실행되는 부하가 문제로 측정되면 ShedLock(JDBC)을 **부하 절감용으로** 추가한다. 그때도 4절의 방어는 유지한다.

**결제 도메인 확인 (dev `9172f6d`)**

결제 도메인 코드에도 분산락은 없고 위 판단이 그대로 유지된다. 두 인스턴스가 모두 1초 폴링과 모든 스캔을 돌리며, 같은 대상을 함께 집어도 아래 장치로 한 번만 반영된다.

| 장치 | 적용 위치 | 막는 것 |
|---|---|---|
| 원자적 꺼내기 | `RedisDelayQueue.popDue`(Lua: `ZRANGEBYSCORE` + `ZREM`) | 두 Worker가 같은 큐 작업을 동시에 꺼내기 |
| 행 잠금 후 상태 재검사 | PAYMENT(만료·확정·웹훅·환불), `MEMBER_POINT → POINT_EARNING`(포인트 만료), `MEMBER → 이용권 → 회차 → 시도`(멤버십 접수·TX2·정리), `MEMBER → BILLING_KEY → BILLING_KEY_DELETION` | 같은 상태 전이의 이중 반영, 재고·포인트 이중 복구, 잔액 이중 차감 |
| 선점(claim)과 임대 | 시도 `READY → DISPATCHING`(시도 PK 잠금, `lease_expires_at`), 웹훅 이벤트 처리권 번호, 빌링키 삭제 처리권 토큰 + 5분 임대, 멤버십 환불 임대·다음 확인 시각 | 같은 외부 호출을 두 실행자가 함께 보내기, 멈췄다 돌아온 실행자의 결과 덮어쓰기 |
| UNIQUE | `webhook_id`, 회차 `(membership_id, period_start_date)`·`idempotency_key`, 시도 `portone_payment_id`·`(membership_payment_id, attempt_no)`, `billing_key_deletion.billing_key_id` | 같은 웹훅·회차·시도·삭제 요청의 두 번째 행 |
| 외부 멱등 | 같은 결제 ID 재조회(새 ID로 재POST 안 함), 환불 행마다 고정된 PortOne 멱등 키 | 이중 승인·이중 환불 |

- 원자적 꺼내기는 동시에 꺼내는 것만 막는다. Fallback Sweep이 처리 중인 작업을 다시 넣을 수 있어, Sweep은 정상 처리라면 이미 끝났어야 할 만큼 오래된 행만 고르고 실제 반영은 위 행 잠금이 한 번으로 줄인다.
- 스케줄러 스레드 분리(기본 1개, 포인트 1개, 멤버십 2개, 빌링키 삭제 2개)는 서로의 지연을 막기 위한 것이지 중복 방지 수단이 아니다. 같은 대상의 중복 처리는 DB 잠금이 막는다.

## 6. 테스트

- 각 작업은 같은 대상에 대해 배치를 두 스레드로 동시에 실행하고, 결과가 한 번 실행한 것과 같은지 검증한다.
- 결제 항목(4-4~4-7, 4-9)의 현재 테스트는 아래와 같다. 동시성 테스트는 실제 MySQL(지연 큐는 실제 Redis) 위에서 스레드마다 독립 트랜잭션으로 경합시키고, PortOne은 Mock이나 스텁 HTTP 서버로 대신한다.

| 대상 | 테스트 클래스 | 확인하는 것 |
|---|---|---|
| 4-4 큐 | `RedisDelayQueueTest` | 실제 Redis에서 `ZADD LT`는 더 이른 시각만 받음, `popDue`는 실행 시각이 지난 작업만 꺼내면서 지움 |
| 4-4 등록 | `PaymentExpirationScheduleListenerTest`, `PaymentExpirationEventIntegrationTest` | 커밋되면 `PAYMENT_EXPIRE` 등록, 롤백이면 미등록, Redis 실패는 밖으로 던지지 않음 |
| 4-4 Worker·Sweep | `PaymentDelayQueueWorkerTest`, `PaymentFallbackSchedulerTest`, `PaymentDelayQueueTest` | 작업 종류별 라우팅, 한 작업 예외 격리, 10분 지난 `IN_PROGRESS` 재등록, keyset 이어 읽기, Redis 장애 시 Sweep 중단 |
| 4-4 판정 | `PaymentExpirationFacadeTest`, `PaymentExpirationProcessorTest` | PortOne 결과별 분기(404·READY 만료, PAID·FAILED 확정 Processor, `CURRENCY_MISMATCH`·`ORDER_MISMATCH`·PG 취소는 `MANUAL_REVIEW`, 10분 뒤 `STALLED`) |
| 4-4 흐름·동시성 | `PaymentExpirationFlowIntegrationTest` | READY 만료 반영, 만료 시각 이내 `paidAt`은 정상 승인, 만료 뒤 승인은 `SYSTEM` 환불 실행, 큐 유실 뒤 Fallback 복구, 확정과 만료 동시 실행 시 한쪽만 반영·재고 이중 복구 없음 |
| 4-5 | `PointExpirationConcurrencyTest` | 만료 배치와 포인트 사용 동시 실행, 두 서버의 만료 배치가 같은 회원을 동시에 처리해도 한쪽만 소멸 |
| 4-5 | `PointExpirationSchedulerTest` | 회원 ID keyset, 회원별 실패 격리, 대상 조회 실패 시 종료, 전용 스케줄러 지정과 `applicationTaskExecutor` 존재 |
| 4-6 | `MembershipChargeConcurrencyTest` | 같은 멱등 키 동시 구독 → 회차·시도 1건·POST 1회, 같은 READY 동시 실행 → 선점 1건, READY 만료와 선점 동시 실행 |
| 4-6 | `MembershipRenewalConcurrencyTest` | 두 스케줄러가 같은 갱신 후보를 집어도 회차 1·시도 1·POST 1·연장 1, 세 Worker 동시 실행 → POST 1회 |
| 4-6 | `MembershipChargeFlowIntegrationTest` | L+M 넘긴 `DISPATCHING`은 같은 ID GET만(POST 0), 플래그 OFF면 POST 없음, 접수 유효 창 30분 경계 |
| 4-6 | `TrialConversionIntegrationTest`, `MembershipRenewalIntegrationTest` | 체험 전환 접수·카드 없음 종료·플래그 OFF 0건, 갱신 재시도 24시간 경계, 세 번 확정 실패 시 회차 `FAILED`, 같은 결과 재수신 시 재시도 시각·시도 수 불변 |
| 4-6·4-7 | `MembershipReconciliationRecoveryIntegrationTest` | 결과 불명 창 24시간 경계, 마감 누락 이용권 `FAILED` 회차 1건, 빌링키 폐기 판정 |
| 4-7 | `PaymentWebhookConcurrencyTest`, `RefundWebhookConcurrencyTest`, `PaymentRecoveryIntegrationTest` | 같은 `webhook_id` 동시 5건 → 행·큐 작업 1개, 확정 API와 웹훅 동시 → 승인 1회, 큐 유실·Worker 중단 뒤 Fallback 복구 |
| 4-7 | `MembershipWebhookAdminIntegrationTest`, `MembershipRefundConcurrencyTest`, `MembershipRefundRecoveryIntegrationTest` | 멤버십 웹훅 재전송 멱등, 실행기·취소 웹훅·복구 스캔 동시 반영 시 완료 1회·추가 POST 없음, 큐 유실 뒤 스캔 복구 |
| 4-9 | `BillingKeyDeletionIntegrationTest` | 다섯 실행자가 같은 요청을 집어도 임대 안에서는 DELETE 1회 |

- 초안의 "PortOne 목이 같은 결제 ID의 두 번째 승인을 거절" 검증은 결정적 ID를 쓰지 않아 해당이 없다. 실제 PortOne의 같은 ID 재POST 응답은 자동 테스트로 재현하지 못한 항목이다(7절).

## 7. 결정 상태와 남은 확인

| 항목 | 담당 | 내용 |
|---|---|---|
| 컨벤션 규칙 | 팀 | 반영됨. [컨벤션](convention.md#스케줄러)은 각 담당자가 작업에 맞는 락·중복 처리 방식을 결정·검증하도록 정함. 결제는 선점·COMMIT·외부 호출·같은 ID 재조회로 방어(3절) |
| 블루그린 선행 조건 문구 | 배포 | [배포 전략](deploy-strategy.md)의 스케줄러 조건을 중복 실행 방어·작업별 검증으로 정리. 실제 블루그린 도입 여부·메모리 여유 확인과 구분 |
| 결제 만료 구현 방식 | 정윤 | 결정·구현됨. Redis 지연 큐 + DB Fallback Sweep(4-4) |
| 포인트 만료 배치 정책 | 정윤 | 구현됨(4-5) |
| PortOne 결제 ID 규칙 | 정윤 | 시도마다 새 ID(`mpay_` + UUID)로 정함(4-6 ③). 남은 것: PortOne 실험 E-3(`mpay_` ID 허용·`paidAt` 표기), E-4(거절 결제의 GET 응답), E-6(같은 ID 재POST 응답). 확인 필요 |
| 멤버십 자동결제·빌링키 정리 플래그 | 정윤, 배포 | `MEMBERSHIP_BILLING_ENABLED`·`BILLING_KEY_DELETION_ENABLED` 모두 기본 `false`. `deploy/`와 `docker-compose.yml`에는 이 값을 넘기는 설정이 없어 켤 때 배포 설정 추가가 필요하다 |
| TRIAL_BLOCK upsert | 채빈 | 구현됨. 활성화 이력일 때만 호출하고 GREATEST로 기간 유지·연장(4-2) |
| 일반 전환 판정 시점 | 채빈 | 구현됨. MembershipService·Repository의 시각 조건으로 혜택 판정. 별도 일반 전환 배치 없음 |
