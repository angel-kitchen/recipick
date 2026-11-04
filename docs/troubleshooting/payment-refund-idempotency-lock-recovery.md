
# 환불 멱등 키 동시 요청의 잠금 획득 실패와 TX1·TX2 복구

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 서로 다른 결제의 동일 멱등 키 취소 중 잠금 획득 실패 · **최정윤** · 2026-10-29(최초 CI 실패), 2026-11-04(수정·검증) |
| 영향 범위 | `POST /api/payments/{paymentId}/cancel`, 마트 거절 환불 접수, `PAYMENT`·`ORDERS`·`REFUND`, PortOne 취소 결과 반영, CI 동시성 테스트 |
| 관련 이슈·PR·커밋 | [문서화 #459](https://github.com/angel-kitchen/recipick/issues/459) · [장애 #437](https://github.com/angel-kitchen/recipick/issues/437) · [PR #455](https://github.com/angel-kitchen/recipick/pull/455) · [TX1 보완 `42d2e42`](https://github.com/angel-kitchen/recipick/commit/42d2e4271ed0e2ab31692f74a3e08eaf5ca0fcfe) · [TX2 보완 `ccfcce8`](https://github.com/angel-kitchen/recipick/commit/ccfcce85c9387f357830f37b19546864bc661c46) · [거절 기록 `135294c`](https://github.com/angel-kitchen/recipick/commit/135294c00aa5ad23406c1f63a10b2049afcbaac5) |
| 처리 상태 | **장애 대응 코드 PR #455 `dev` 병합 완료·병합 후 CI 성공, 문서화 이슈 #459 진행 중**(2026-11-04 기준). 최초 교착의 정확한 잠금 관계는 미확정 |

## 1. 증상

- **기대한 동작과 실제 동작:** 서로 다른 `PAID` 결제 두 건에 동일한 `Idempotency-Key`로 회원 취소 요청이 동시에 도착하면, 한 건만 취소되고 다른 건은 `COMMON-011`(같은 키로 다른 요청)로 거절돼야 한다. 그런데 `PaymentCancelAfterPayConcurrencyTest.sameKeyOnTwoPaidPayments()`가 CI에서 간헐적으로 실패했고, 한 스레드에서 정상적인 UNIQUE 충돌 예외 대신 DB 잠금 획득 예외가 발생했다.
- **발생 환경·버전·재현 조건:** Spring Boot 3.5·Hibernate/JPA·MySQL InnoDB. CI는 Testcontainers **MySQL 8.4**(`innodb_lock_wait_timeout=5`). 실패는 2026-10-29 `dev` [Actions 실행](https://github.com/angel-kitchen/recipick/actions/runs/40876371921)과 2026-11-03 [PR #435의 첫 번째 Actions 실행](https://github.com/angel-kitchen/recipick/actions/runs/41662203731)에서 관찰됐다. 후자의 재실행은 통과했다.
- **재현 명령·요청·순서:** 두 `PAID` 결제를 준비한 뒤 두 스레드가 같은 멱등 키로 `PaymentCancelFacade.cancel()`을 호출한다. 해당 테스트는 PortOne을 Mock으로 대체하고 **실제 MySQL 트랜잭션과 UNIQUE 제약**을 사용한다.

  ```bash
  ./gradlew test --tests 'com.recipick.domain.payment.facade.PaymentCancelAfterPayConcurrencyTest'
  ```

- **핵심 오류 로그·관찰 결과:** CI의 짧은 실패 로그에는 다음 예외 사슬이 기록됐다. 테스트는 Facade를 직접 호출하고 `BusinessException`만 수집하므로 잠금 예외가 `Future.get()`의 `ExecutionException`으로 드러났다. **실제 HTTP 경로에서는 기존 `GlobalExceptionHandler`가 해당 잠금 예외 계열을 409 `COMMON-010`으로 변환한다.**

  ```text
  PaymentCancelAfterPayConcurrencyTest > 두 PAID 결제에 같은 키로 동시에 오면 ... FAILED
      java.util.concurrent.ExecutionException at PaymentCancelAfterPayConcurrencyTest.java:317
          Caused by: org.springframework.dao.CannotAcquireLockException at PaymentCancelAfterPayConcurrencyTest.java:206
              Caused by: org.hibernate.exception.LockAcquisitionException
                  Caused by: com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException
  ```

- **로컬 재현 결과:** 수정 전 동일 테스트를 MySQL 8.0 및 Testcontainers MySQL 8.4 환경에서 단독·결제 패키지 동반 실행 등 **총 830회** 반복했지만 잠금 예외가 재현되지 않았다. MySQL 8.4에서 반복 테스트 로그에 일반적인 중복 키 오류 `1062 / SQLState 23000`은 관찰됐다. **원래 CI 예외의 JDBC 오류 번호(1213/1205)와 `LATEST DETECTED DEADLOCK` 잠금 그래프는 확보하지 못했다.**

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 같은 결제 행을 두 요청이 동시에 수정해 충돌한다 | `PaymentCancelProcessor.prepare()`의 결제 ID 비관적 잠금 확인 | 테스트는 **서로 다른 결제**를 잠근다. 결제 PK 락만으로 같은 키의 전역 경합을 직렬화할 수 없음 · 확인 |
| 서로 다른 결제가 같은 환불 멱등 키를 사용한다 | `PaymentService.findRefundByIdempotencyKey()` 조회와 `requestMemberRefund()`의 `saveAndFlush()` 확인 | 미커밋 행은 일반 조회에 보이지 않을 수 있다. 최종 중복 방어는 `REFUND.idempotency_key` UNIQUE 인덱스가 수행 · 확인 |
| 중복 키 충돌은 항상 `COMMON-011`로 변환된다 | `PaymentService.requestMemberRefund()` 예외 처리 확인 | `DataIntegrityViolationException`만 `COMMON-011`로 변환. `CannotAcquireLockException`은 이 분기에 포함되지 않음 · 확인 |
| CI 실패는 특정 InnoDB 데드락(1213) 때문이다 | Hibernate 예외 사슬과 CI 출력 확인, 로컬 반복 실행 | **데드락 가능성이 높지만 미확정.** 실제 오류 번호와 두 트랜잭션의 대기 그래프가 없음 |
| PG 취소 후 DB 반영(TX2)이 실패해도 즉시 복구가 예약된다 | 수정 전 `PaymentCancelFacade.cancel()`의 `apply()` 호출과 PENDING 예약 위치 확인 | `apply()`가 예외를 던지면 다음 예약문에 도달하지 못한다. DB Fallback에 의존하는 복구 지연 가능성 · 확인 |
| PG가 HTTP 400·401·403으로 거절한 사실은 나중에 GET으로 복구할 수 있다 | `PortOneCancelResult.fromError()`와 `RefundReconciliationFacade`의 취소 내역 조회 확인 | 처리 전 거절에는 취소 내역 ID가 없어, 원래 거절 사실을 단순 GET만으로 복원할 수 없음 · 확인 |

**확인된 원인:** `PAYMENT`별 비관적 잠금과 `REFUND.idempotency_key`의 전역 UNIQUE 제약이 맡는 범위가 달라, 서로 다른 결제의 동일 키 경쟁은 환불 INSERT에서 최종 판정된다. 기존 서비스는 일반적인 UNIQUE 위반만 업무 예외로 변환했으며, 잠금 실패 시 트랜잭션 재시도와 **PG 호출 후 DB 결과 반영 실패에 대한 즉시 복구**가 충분하지 않았다.

**미확정 사항:** 두 CI 실패를 실제로 일으킨 특정 잠금 순환, 관련 인덱스, 교착 희생 트랜잭션(TX1 또는 TX2)은 확인되지 않았다. 따라서 이번 변경을 **데드락 자체의 원인 제거**로 설명하지 않는다.

## 3. 해결

- **바꾼 내용과 이유 — TX1(취소 접수):** `PaymentCancelFacade`와 `MartRejectionFacade`에서 `PessimisticLockingFailureException` 발생 시 100ms 간격으로 `prepare()`를 **최대 총 2회(최초 1회 + 재시도 1회)** 호출하도록 했다. Facade에는 `@Transactional(propagation = Propagation.NEVER)`를 적용해 기존 트랜잭션에 합류하지 못하게 했다. 매번 결제 잠금·권한/소유권·멱등 키 `replay()`·UNIQUE 판정을 처음부터 다시 수행한다. 재시도가 모두 실패하면 `COMMON-010`으로 응답하며 **TX1 실패 경로에서는 PortOne 취소를 호출하지 않는다.**
- **바꾼 내용과 이유 — TX2(취소 결과 반영):** PortOne 취소 POST는 루프 바깥에서 **한 번만 호출**하고, 이미 받은 응답을 사용해 `PaymentCancelProcessor.apply()`만 총 2회까지 재시도한다. 재시도 소진 시 PG 결과에 따라 후속 동작을 분리했다.

  | PortOne 결과 | 재시도 소진 후 처리 | API 결과 |
    | --- | --- | --- |
  | `SUCCEEDED`, `REQUESTED`, `MISMATCH`, `UNCONFIRMED` | `REFUND_RECONCILE` 즉시 예약 후 현재 결제·환불 상태 조회 | 미완료 시 `202 PENDING`; 완료 상태 및 PG 증거 충돌은 별도 검사 |
  | `FAILED` | `REFUND_RECONCILE` 즉시 예약. PG 조회에서 취소 실패 내역을 확인해 DB에 반영 | `409 COMMON-010`(DB 반영 미완료) |
  | `REJECTED` | 별도 트랜잭션으로 `REFUND`에 거절 증거 기록 후 즉시 예약 | `409 COMMON-010`(DB 반영 미완료) |

- **바꾼 내용과 이유 — REJECTED 증거 유실 방지:** `Refund`에 `recordPendingRejection()`·`hasPendingRejection()`을 추가했다. TX2가 실패한 뒤 별도 트랜잭션에서 **`REFUND` 행만 PK 잠금**으로 읽어 `status=REQUESTED`를 유지하면서 `failureType=REQUEST_REJECTED`, `failureReason=PG_REQUEST_REJECTED`를 기록한다. 재확인 Worker는 이 표시가 있으면 PortOne GET·POST 없이 `PaymentCancelProcessor.apply(REJECTED)`를 호출해 환불 `FAILED`, 결제·주문 `PAID` 복귀를 확정한다. 같은 키 재요청은 이 표시가 있는 동안 PortOne을 재호출하지 않는다. 이미 다른 경로가 완료한 환불의 증거는 덮어쓰지 않는다.
- **진단 보강:** `LockFailureDetail`에서 원인 `SQLException`의 `errorCode`·`SQLState`를 추출해 구간(TX1/TX2), ID, 재시도 횟수와 함께 기록한다. **잠금 실패를 모두 `COMMON-011`로 바꾸지 않는다.** 이는 실제 키 충돌과 일반 DB 잠금 장애를 구분하기 위해서다.
- **다른 대안을 선택하지 않은 이유:** 같은 키 경쟁을 막겠다고 모든 환불에 전역 락을 추가하면 무관한 결제까지 직렬화할 수 있다. PG 취소 POST를 무조건 재호출하면 이미 처리된 환불을 다시 요청할 위험이 있다. 예외를 테스트에서 무시하면 실제 복구 누락을 숨긴다.
- **변경 코드·설정·문서:** [`PaymentCancelFacade`](../../src/main/java/com/recipick/domain/payment/facade/PaymentCancelFacade.java), [`PaymentCancelProcessor`](../../src/main/java/com/recipick/domain/payment/facade/PaymentCancelProcessor.java), [`MartRejectionFacade`](../../src/main/java/com/recipick/domain/payment/facade/MartRejectionFacade.java), [`Refund`](../../src/main/java/com/recipick/domain/payment/entity/Refund.java), [`PaymentService`](../../src/main/java/com/recipick/domain/payment/service/PaymentService.java), [`RefundReconciliationFacade`](../../src/main/java/com/recipick/domain/payment/facade/RefundReconciliationFacade.java), [`LockFailureDetail`](../../src/main/java/com/recipick/domain/payment/facade/LockFailureDetail.java), [PR #455의 전체 변경](https://github.com/angel-kitchen/recipick/pull/455/files).
- **기존 데이터·운영에 필요한 조치:** 신규 테이블·DB 컬럼·API 없음. 기존 `REFUND.failure_type`·`failure_reason`을 재사용한다. `build.gradle`과 CI 워크플로는 이번 PR에서 변경하지 않았다. 운영에서는 거절 증거 저장 실패 또는 상태 불일치 ERROR 로그의 `paymentId`·`refundId`를 기준으로 확인해야 한다.

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 기존 CI 간헐 실패 | `PaymentCancelAfterPayConcurrencyTest.sameKeyOnTwoPaidPayments()` | 10/29·11/03 최초 CI에서 잠금 획득 예외로 실패. 재실행은 통과할 수 있었음 | PR #455에서 전체 CI `test` 1회 성공. **원래 데드락 미재현·재발 불가 증명은 아님** | [실패 CI(10/29)](https://github.com/angel-kitchen/recipick/actions/runs/40876371921) · [실패/재실행 CI(11/03)](https://github.com/angel-kitchen/recipick/actions/runs/41662203731) · [PR #455 CI](https://github.com/angel-kitchen/recipick/actions/runs/41791252918) |
| TX1 잠금 실패·멱등 키 충돌 | `PaymentCancelLockFailureTest`의 T1·T2·T2b | TX1 잠금 실패 후 새 업무 트랜잭션 재시도 없음 | 실제 MySQL 행 잠금·대기 초과로 1회 재시도 성공/소진 `COMMON-010`, 미커밋 UNIQUE 키 충돌 `COMMON-011` 검증 | [신규 통합 테스트](../../src/test/java/com/recipick/domain/payment/integration/PaymentCancelLockFailureTest.java) |
| TX2 잠금 실패와 PG 호출 횟수 | `PaymentCancelLockFailureTest`의 T3·T4·T6 | TX2 예외 시 PG 결과 반영·재확인 예약으로 이어지지 못할 수 있음 | PG POST 1회 유지, DB 반영 재시도 성공 및 소진 시 SUCCEEDED·FAILED 후속 재확인/최종 정합성 확인 | [신규 통합 테스트](../../src/test/java/com/recipick/domain/payment/integration/PaymentCancelLockFailureTest.java) |
| REJECTED 유실·동일 키 재요청 | `PaymentCancelLockFailureTest`의 T5 | TX2 예외 뒤 취소 내역 없는 PG 거절 결과를 자동 복원하기 어려움 | 환불 행에 거절 증거 저장 → 같은 키 재요청 POST 차단 → Worker가 PG 호출 없이 `FAILED`·`PAID` 복귀 | [신규 통합 테스트](../../src/test/java/com/recipick/domain/payment/integration/PaymentCancelLockFailureTest.java) |
| 관리자 거절 TX2 안전망 | `PaymentCancelLockFailureTest`의 T7 | 기존 실행기에서 재실행 예약·PENDING 처리 | 기존 안전망을 변경하지 않고 잠금 실패 후 예약·Worker 완료 검증 | [신규 통합 테스트](../../src/test/java/com/recipick/domain/payment/integration/PaymentCancelLockFailureTest.java) |
| 기본 5초 잠금 대기 경로 | `PaymentCancelLockFailureDefaultTimeoutTest` | 전용 회귀 테스트 없음 | 실제 MySQL 기본 5초 대기에서 TX1 재시도 소진·롤백·PG 미호출 확인 | [기본 시간 제한 테스트](../../src/test/java/com/recipick/domain/payment/integration/PaymentCancelLockFailureDefaultTimeoutTest.java) |
| 전체 회귀 검증 | `./gradlew test` | 원래 CI에서 간헐 실패 존재 | **작성자 PC 보고:** 328개 클래스·3,433건, 실패 0, 건너뜀 7 (Testcontainers MySQL 8.4). **GitHub PR #455 CI 및 병합 후 dev CI:** `test` 성공(2026-11-04). 단, CI에서 동일한 세부 집계를 별도로 확인한 것은 아님 | [PR #455](https://github.com/angel-kitchen/recipick/pull/455) · [PR CI](https://github.com/angel-kitchen/recipick/actions/runs/41791252918) · [병합 후 dev CI](https://github.com/angel-kitchen/recipick/actions/runs/41803968624) |

테스트 실행 명령(프로젝트 루트):

```bash
./gradlew test --tests 'com.recipick.domain.payment.integration.PaymentCancelLockFailureTest' \
  --tests 'com.recipick.domain.payment.integration.PaymentCancelLockFailureDefaultTimeoutTest' \
  --tests 'com.recipick.domain.payment.facade.PaymentCancelAfterPayConcurrencyTest'
./gradlew test
```

새 잠금 테스트는 실제 DB 잠금과 트랜잭션 롤백을 사용한다. `PaymentCancelLockFailureTest` 8건은 **해당 테스트 컨텍스트의 연결**에 잠금 대기 제한 1초를 적용했고, 별도 1건은 기본 5초를 사용했다. 이 검증이 입증하는 것은 **잠금 실패 발생 후 처리·복구 동작**이지, 원래 CI의 1213 교착이 사라졌다는 사실은 아니다.

## 5. 재발 방지와 남은 작업

- **추가한 테스트·알림·문서·규칙:** 실제 MySQL에서 락 보유 스레드와 `CountDownLatch`로 충돌을 통제하는 테스트 8건, 기본 5초 테스트 1건을 유지한다. TX1은 PG 호출 전에만 재시도하고, TX2는 PG 재호출 없이 DB 반영만 재시도한다. JDBC 오류 번호와 SQLState를 기록해 `1205`(잠금 대기 초과)·`1213`(데드락)을 구분할 근거를 남긴다.
- **남은 제한과 담당 이슈:** [#437](https://github.com/angel-kitchen/recipick/issues/437)의 최초 실패를 만든 **잠금 그래프와 TX1/TX2 희생자**는 미확정이다. 다음에 재발하면 SQL 예외 번호·스택과 MySQL `SHOW ENGINE INNODB STATUS`의 `LATEST DETECTED DEADLOCK`을 함께 확보해야 한다. `REFUND`에 REJECTED 증거 자체를 저장하지 못하는 DB 장애, 상태가 서로 충돌하는 경합은 자동 복구를 보장하지 않으며 **ERROR 로그 기반 운영 확인**이 필요하다. 복구 예약이 실패하더라도 `REFUND REQUESTED`는 DB에 남고 기존 [Fallback Scheduler](../../src/main/java/com/recipick/domain/payment/scheduler/PaymentFallbackScheduler.java)가 주기적으로 재등록할 수 있지만, 복구가 즉각 완료된다는 보장은 없다.
- **다른 팀원에게 공유할 적용 방법:** **데드락을 없앤 수정이 아니라, 데드락·잠금 대기 초과가 발생했을 때 응답·데이터 정합성 복구를 보강한 수정**으로 설명한다. CI의 `BUILD SUCCESSFUL` 한 번만으로 간헐 장애의 근본 해결을 주장하지 않고, 멱등 키·비관적 락·PG 호출·TX2 커밋·Fallback의 책임을 분리해 검증한다. PR #455는 2026-11-04 `dev`에 병합되었고, 병합 후 [dev CI](https://github.com/angel-kitchen/recipick/actions/runs/41803968624)의 테스트 및 배포 작업도 성공했다. 단, 이는 원래 간헐 교착의 재발 가능성이 0이라는 증명이 아니다.
