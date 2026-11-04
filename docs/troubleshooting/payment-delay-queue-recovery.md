# Redis Delay Queue 작업 유실과 DB Fallback 복구

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | Redis 큐 pop 이후 미처리 작업 복구 · **최정윤** · 2026-10-26(구현·검증 기준) |
| 영향 범위 | 웹훅 처리, 결제 만료, 환불 재확인, `WEBHOOK_EVENT`, Redis Sorted Set |
| 관련 이슈·PR·커밋 | [#173](https://github.com/angel-kitchen/recipick/issues/173) · [PR #199](https://github.com/angel-kitchen/recipick/pull/199) · [복구 구현 `ea29dabc7f`](https://github.com/angel-kitchen/recipick/commit/ea29dabc7f) |
| 처리 상태 | 해결 |

## 1. 증상

- **기대한 동작과 실제 동작:** 웹훅을 큐에서 꺼낸 뒤 결제 상태가 확정돼야 한다. Worker가 큐에서 작업을 제거한 직후 DB 락 대기 초과나 중단이 발생하면 Redis 작업은 사라지고 MySQL 이벤트는 `RECEIVED` 또는 `PROCESSING`으로 남았다.
- **발생 환경·버전·재현 조건:** Redis Sorted Set + Lua Script, MySQL, Spring Scheduler. Worker의 claim 실패 또는 claim 후 중단 조건.
- **재현 명령·요청·순서:** `PaymentRecoveryIntegrationTest.claimLockTimeoutRecoveredByFallback()`에서 웹훅 행을 잠가 Worker claim을 실패시키고, `staleProcessingRecoveredByFallback()`에서 처리권 획득 후 중단 상태를 구성한다.
- **핵심 오류 로그·관찰 결과:** Worker 오류 기록 지점 `지연 큐 작업 처리 중 예외: task=...`. 큐 작업은 제거됐지만 DB 상태는 미완료였다. Redis `ZSCORE payment:delay-queue <member>`와 `webhook_event.status`를 대조해 확인한다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 두 Worker가 같은 작업을 동시에 꺼냄 | `RedisDelayQueue.POP_DUE` Lua 확인 | 조회·제거는 Redis 내에서 원자적으로 실행 |
| 큐 작업 제거가 업무 완료를 의미 | Redis와 `WEBHOOK_EVENT` 상태 비교 | DB 처리는 완료되지 않을 수 있음 · 확인 |
| Worker 예외 시 자동으로 작업 재생성 | `PaymentDelayQueueWorker` 예외 경로 확인 | 프로세스 중단에는 별도 복구 주체 필요 |

확인된 원인: Redis의 원자적 `ZREM`과 MySQL의 결제 상태 커밋은 하나의 트랜잭션이 아니다. 큐만 믿으면 중간 장애 시 후속 처리 신호가 사라진다.

## 3. 해결

- **바꾼 내용과 이유:** 웹훅을 먼저 MySQL Inbox에 커밋하고 Redis에는 작업 종류·ID·실행 시각만 등록한다. `PaymentFallbackScheduler`가 오래된 `RECEIVED`·`RETRY_PENDING`·`PROCESSING`을 DB에서 찾고 다시 큐에 넣는다. Worker는 DB에서 상태를 재확인한다.
- **다른 대안을 선택하지 않은 이유:** Worker의 예외 처리에서만 재등록하면 프로세스 중단을 복구할 수 없다. PG 호출을 무조건 재시도하면 중복 결제·환불 위험이 있다.
- **변경 코드·설정·문서:** [`RedisDelayQueue`](../../src/main/java/com/recipick/infra/redis/RedisDelayQueue.java), [`PaymentDelayQueueWorker`](../../src/main/java/com/recipick/domain/payment/scheduler/PaymentDelayQueueWorker.java), [`PaymentFallbackScheduler`](../../src/main/java/com/recipick/domain/payment/scheduler/PaymentFallbackScheduler.java), [`WebhookEventService`](../../src/main/java/com/recipick/domain/payment/service/WebhookEventService.java).
- **기존 데이터·운영에 필요한 조치:** 큐 유실 건은 DB의 미처리 이벤트를 기준으로 복구한다. 정기 Sweep은 기본 1시간 간격이며 처리 지연은 별도 관찰이 필요하다.

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| pop 후 claim 실패 | `claimLockTimeoutRecoveredByFallback()` | 큐 제거, DB `RECEIVED`, 결제 `IN_PROGRESS` | Sweep 후 재등록·Worker 재처리, DB `PROCESSED`·결제 `PAID` | [테스트](../../src/test/java/com/recipick/domain/payment/integration/PaymentRecoveryIntegrationTest.java) |
| 처리권 획득 후 중단 | `staleProcessingRecoveredByFallback()` | `PROCESSING` 지속, 큐 작업 없음 | 처리권 2로 복구, 이전 처리권 완료 거부 | [테스트](../../src/test/java/com/recipick/domain/payment/integration/PaymentRecoveryIntegrationTest.java) |
| 큐 원자성·재예약 | `RedisDelayQueueTest` | 중복 pop·작업 소실 위험 | Lua 원자적 pop, 더 이른 예약·backoff 검증 | [테스트](../../src/test/java/com/recipick/infra/redis/RedisDelayQueueTest.java) |

## 5. 재발 방지와 남은 작업

- **추가한 테스트·알림·문서·규칙:** 큐 pop 직후 실패와 오래된 처리권을 검증한다. Redis는 실행 신호로만 사용하고 업무 상태는 MySQL을 원본으로 둔다.
- **남은 제한과 담당 이슈:** Sweep 주기 때문에 복구는 즉시 실행되지 않는다. 운영 지연 경보는 후속 개선 대상이다.
- **다른 팀원에게 공유할 적용 방법:** 큐에 신규 작업 종류를 추가할 때 Worker 분기와 DB 미완료 조회·Fallback 재등록까지 함께 작성한다.
