# 결제·웹훅·환불 정합성 설계 결정

상품 결제·회원 취소·마트 거절·SYSTEM 환불·외부 취소·멤버십 자동결제는 모두 "PortOne의 실제 상태"와 "레시픽 DB의 상태"를 맞추는 문제입니다.
결제 도메인은 PortOne 응답이나 웹훅 본문을 그대로 반영하지 않고, 트랜잭션 밖에서 PortOne을 다시 조회한 뒤 결제 행 잠금 아래에서 한 번만 반영합니다.
같은 일을 여러 경로(확정 API·웹훅·만료 작업·Fallback)가 반복해도 결과가 같도록 비관적 락·UNIQUE 제약·멱등 키가 역할을 나눠 맡습니다.

| 항목 | 내용 |
| --- | --- |
| 주제 | 결제·웹훅·환불 정합성: 상태 전이·멱등성·외부 호출·복구·검증 |
| 담당자 | 정윤, 주문 연동 준희 |
| 작성·최종 확인일 | 2026-11-04 |
| 상태 | 구현, 문서 검증·리뷰 대기 |
| 관련 요구사항·이슈·PR | [요구사항](../requirement.md)의 결제·환불·포인트·회원 탈퇴 절, [주문↔결제 연동 기준](../order-payment-integration.md). 결제 확정 #54·#82, 결제 생성 #84, 웹훅 #199, SYSTEM 환불 #214, 결제 만료 #228, 외부 취소 #233, 마트 거절 환불 #247, 포인트 #278, 탈퇴 정산 #302, 빌링키 #318, 멤버십 첫 결제 #341, 갱신 #360, 멤버십 정합화 #380, 멤버십 환불 #388, 통화 검증 #410. 알려진 문제 #437 |
| 검증한 코드 기준 | `dev`의 `9172f6d`(2026-11-04) |

## 사용 기술 한눈에 보기

| 영역 | 기술 | 하는 일 | 고른 이유 | 자세히 |
| --- | --- | --- | --- | --- |
| 판정 근거 | PortOne 결제 단건 조회(GET) | 확정 API·웹훅·만료 작업이 같은 조회 결과로 판정 | 프론트 값·웹훅 본문은 위조·지연·순서 뒤바뀜이 가능 | D1 |
| 지연 실행 | Redis Sorted Set + Lua(`ZADD LT`, 조회·제거 한 스크립트) | 웹훅 처리·만료·환불 재확인 작업 예약 | 여러 인스턴스가 같은 작업을 꺼내지 않고, 예약을 늦추지 않음 | D2 |
| 복구 | `PaymentFallbackScheduler`(1시간, DB 상태 기준) | 큐에서 사라진 작업 재등록 | MySQL이 원본이고 Redis는 실행 신호일 뿐 | D2 |
| 트랜잭션 | 트랜잭션 없는 Facade + `@Transactional` Processor | TX1(접수) → PortOne → TX2(반영) | PG를 기다리는 동안 DB 잠금을 쥐지 않음 | D3 |
| 잠금 | `PESSIMISTIC_WRITE`, 고정 순서 `PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고` | 같은 결제를 바꾸는 모든 경로 직렬화 | 잠근 뒤 상태 재검사로 한쪽만 반영 | D4 |
| 제약 | UNIQUE `refund.idempotency_key`·`refund.portone_cancellation_id`·`webhook_event.webhook_id` 등 | 같은 작업·같은 사건의 행 하나 | 서로 다른 행을 잠근 경합의 최종 방어 | D4 |
| 외부 멱등 | PortOne 멱등 키 `recipick-refund-{환불 ID}` | 환불 행 하나 = PortOne 취소 하나 | 같은 환불을 여러 경로가 다시 보내도 PG에서 한 건 | D4, D5 |
| 검증 | `PaymentVerifier` + `PaymentApprovalOutcome` | 주문 ID·KRW·금액·완료 시각 판정 | 판정 한 곳, 집행은 값을 받는 switch 식 | D5 |
| 자동결제 | 시도 행 선점(READY → DISPATCHING, 임대) | PG POST는 선점한 한 Worker만 | 결과 불명 시 같은 결제 ID로 재조회만, 새 ID 재청구 없음 | D6 |

## 1. 상황

결제 결과는 PortOne이 정하고, 레시픽은 그 결과를 결제·주문·포인트·재고·멤버십에 반영합니다. 같은 사실이 여러 번, 여러 경로로, 늦게 도착합니다.

- 기능과 요청 흐름
  - 상품 결제: 주문 생성(결제 `IN_PROGRESS`, 5분 만료) → 결제창 → 확정 API 또는 `Transaction.Paid` 웹훅 → `PAID`. 결제하지 않으면 만료 작업이 닫습니다.
  - 결제 후 회원 취소·마트 거절: 접수(`CANCEL_REQUESTED`·환불 `REQUESTED`) → PortOne 전액 취소 → 결과 반영. 결과 불명이면 웹훅·재조회가 마무리합니다.
  - SYSTEM 환불: 만료 뒤 늦은 승인·금액 불일치처럼 돈은 받았는데 주문을 살릴 수 없는 경우 PG 금액을 돌려줍니다.
  - 외부 취소: PortOne 콘솔·관리자 화면에서 끝난 전액 취소를 DB에 맞춥니다.
  - 멤버십: 빌링키 등록, 바로 구독·체험 전환·월 갱신의 자동 청구, 이용료 환불.
- 일관성 제약
  - 돈이 나갔는데 주문이 살아 있거나, 돈을 받았는데 주문이 닫힌 채 돌려주지 않는 상태가 남으면 안 됩니다.
  - 같은 승인·실패·환불이 확정 API·웹훅·만료 큐·Fallback으로 반복·동시에 와도 재고·포인트·이용권은 정확히 한 번만 바뀌어야 합니다.
  - 같은 환불이나 자동 청구를 PortOne에 두 번 요청해 이중 환불·이중 결제가 생기면 안 됩니다.
  - PortOne 호출의 timeout·5xx·응답 유실은 "실패"가 아니라 "모름"입니다.
- 운영 제약: 앱 서버 여러 대와 블루그린 겹침을 전제로 JVM 락·분산 락을 쓰지 않습니다. 컨벤션상 낙관적 락·`NOWAIT`·`REQUIRES_NEW`는 쓰지 않습니다. PortOne은 30초 안에 응답하지 않은 웹훅을 다시 보냅니다(`PortOneWebhookFacade` 주석 기준).
- 해결됐다고 판단할 기준: 위 경합을 실제 MySQL·Redis 통합 테스트로 재현해 결제 상태 전이 1회·환불 행 1건·재고 1회 복구·SYSTEM 환불 1건·자동 청구 POST 1회가 지켜지는 것. 결과는 [동시성 문서 6절](../concurrency.md)에 모읍니다.

## 2. 후보

### 2-1. 판정 근거 (D1)

| 후보 | 우리 상황에서의 장점 | 비용·한계 |
| --- | --- | --- |
| 프론트가 보낸 결제 결과·금액으로 확정 | 호출이 가장 적음 | 위조 가능. 금액 검증 근거가 없음 |
| 웹훅 본문의 상태를 그대로 반영 | PortOne 재조회가 필요 없음 | 서명이 맞아도 순서가 뒤바뀌거나 늦게 오고, 본문은 사건 알림일 뿐 최종 상태가 아님 |
| **웹훅·API는 신호로만 쓰고 PortOne 단건 조회 결과로 판정(선택)** | 모든 경로가 같은 근거·같은 판정 | 처리마다 PortOne 조회 1회. 조회 장애 시 재시도 필요 |

### 2-2. 지연 실행과 유실 복구 (D2)

| 후보 | 우리 상황에서의 장점 | 비용·한계 |
| --- | --- | --- |
| `@Scheduled` 폴링만(1분마다 DB 스캔) | 구성 단순 | 5분 만료·1분 재조회처럼 건별 시각이 다른 작업을 정확히 맞추려면 짧은 주기로 전체를 훑어야 함 |
| 메시지 브로커(Kafka·RabbitMQ) | 재전송·지연 전달 기능 | 새 인프라 운영. DB 커밋과 메시지 발행의 원자성은 여전히 따로 해결해야 함 |
| Redis 큐만 신뢰 | 이미 쓰는 Redis, 지연 실행 정확 | 꺼낸 뒤 장애·등록 실패 시 작업이 사라짐 |
| **Redis Sorted Set 지연 큐 + DB 원본 + 저빈도 Fallback Sweep(선택)** | 실행 시각은 Redis, 진실은 DB. 큐가 비어도 DB 상태로 다시 등록 | Worker·Fallback 두 경로를 유지하고, 각 작업이 반복 실행에 멱등이어야 함 |

### 2-3. 외부 호출과 DB 트랜잭션 (D3)

| 후보 | 우리 상황에서의 장점 | 비용·한계 |
| --- | --- | --- |
| 한 트랜잭션 안에서 PortOne 호출 | 코드가 단순하고 롤백이 한 번 | PG 응답(초 단위)을 기다리는 동안 결제·주문 행 잠금 유지. PG는 성공했는데 DB가 롤백되면 돈만 나감 |
| PortOne 호출 뒤 한 트랜잭션으로 반영(접수 기록 없음) | 잠금 시간 짧음 | 호출 중 서버가 죽으면 "취소를 요청했다"는 기록이 없어 재조회·재시도 근거가 사라짐 |
| `REQUIRES_NEW`로 접수만 먼저 커밋 | 한 메서드 안에서 경계 분리 | 컨벤션상 금지. 바깥 트랜잭션 잠금을 쥔 채 PG를 기다리는 구조가 숨어 들어옴 |
| **트랜잭션 없는 Facade가 TX1(접수 커밋) → PortOne → TX2(반영)를 조립(선택)** | 잠금 없이 PG 대기, 접수 행이 재처리 근거 | 같은 결제를 TX1·TX2 두 번 잠그고, TX2는 그 사이 다른 경로가 끝낸 경우를 판정해야 함 |

### 2-4. 경합 방어 수단 (D4)

| 후보 | 우리 상황에서의 장점 | 비용·한계 |
| --- | --- | --- |
| 낙관적 락(`@Version`) | 대기 없음 | 컨벤션상 금지. 충돌 후 재시도·재검증이 경로마다 필요 |
| 조건부 UPDATE만 | 한 문장으로 원자적 | 결제·주문·포인트·재고를 함께 바꾸는 다중 행 전이를 표현하기 어려움 |
| Redis 분산 락 | 인스턴스 간 직렬화 | TTL이 지나면 풀려 정합성을 보장하지 못하고 결국 행 단위 방어가 따로 필요 |
| **기존 행 비관적 락 + 고정 잠금 순서 + 새 행 UNIQUE + 업무 행에 저장한 멱등 키(선택)** | 잠근 뒤 현재 상태로 판정. 서로 다른 행을 잠근 경합은 UNIQUE가 최종 판정 | 같은 결제의 요청은 기다리고, 잠금 순서·`MANDATORY` 참여 규칙을 모든 경로가 지켜야 함 |

### 2-5. 취소·환불·늦은 승인 (D5)

| 후보 | 우리 상황에서의 장점 | 비용·한계 |
| --- | --- | --- |
| PG 결과 불명을 실패로 보고 `PAID` 복귀 | 상태가 빨리 정리됨 | 실제로 취소됐으면 돈은 나갔는데 주문이 살아 있음 |
| 만료 뒤 승인을 주문 부활로 처리 | 회원은 결제 성공 | 이미 복구한 재고·포인트를 다시 바꿔야 하고 다른 회원 주문과 재고가 충돌 |
| **불명은 상태 유지 후 재조회, 명확한 실패만 복귀. 늦은 승인·금액 불일치는 주문을 닫은 채 SYSTEM 환불(선택)** | 돈과 주문이 어긋나는 방향의 오류를 만들지 않음 | 결과 확정까지 `CANCEL_REQUESTED`가 오래 남을 수 있고 운영 확인(`MANUAL_REVIEW`) 경로가 필요 |

### 2-6. 멤버십 자동 청구 (D6)

| 후보 | 우리 상황에서의 장점 | 비용·한계 |
| --- | --- | --- |
| 분산 락으로 스케줄러 단일 실행 | 중복 실행 자체를 줄임 | TTL 만료·GC 정지 시 두 서버가 함께 청구. 락만으로는 이중 결제를 막지 못함 |
| 회차 행 하나에 시도 횟수를 두고 조건부 UPDATE로 선점 | 테이블 하나 | 시도마다 다른 PortOne 결제 ID·결과를 추적하기 어려움 |
| **회차 UNIQUE + 시도 행 PK 잠금 선점(임대) + 시도별로 미리 저장한 결제 ID로만 POST·GET(선택)** | 선점한 하나만 POST. 결과 불명이면 같은 ID로 재조회만 | 시도·회차·환불 상태가 많아지고, 결과 불명 창·강한 차단 같은 운영 상태를 함께 관리 |

## 3. 선택과 이유

### 3-1. 웹훅 본문은 신호, 판정은 PortOne 재조회 (D1)

- 선택: 확정 API·웹훅·만료 작업은 모두 PortOne 단건 조회 결과를 같은 Processor에 넘깁니다. 프론트 값과 웹훅 본문의 상태는 판정에 쓰지 않습니다.
- 선택한 이유: 세 경로가 같은 근거와 같은 잠금으로 수렴해야 "먼저 온 쪽이 반영, 나머지는 멱등"이 성립합니다. 웹훅 본문은 어느 결제·환불을 볼지 고르는 데만 씁니다.
- 적용한 구조·실패 처리
  - 수신: [PortOneWebhookFacade](../../src/main/java/com/recipick/domain/payment/facade/PortOneWebhookFacade.java)는 서명 검증 → 저장 → 큐 등록 → 200까지만 합니다. PortOne 재조회·잠금을 하지 않습니다. 서명 실패는 `AUTH-010`(401)로 DB에 남기지 않고, storeId가 다르거나 처리하지 않는 type도 `IGNORED`로 저장해 재전송이 UNIQUE에서 끝나게 합니다. storeId 설정이 없으면 `PAYMENT-011`(500)로 거절해 설정 뒤 재전송을 받습니다.
  - 처리: [PaymentReconciliationFacade](../../src/main/java/com/recipick/domain/payment/facade/PaymentReconciliationFacade.java)가 TX-A(행 잠금 뒤 `PROCESSING` 선점) → PortOne 조회(잠금 없음) → TX-B(기존 Processor) → TX-C(결과 기록)로 처리합니다. 선점 때 증가한 `attemptCount`를 처리권 번호로 써서, 멈췄다 돌아온 Worker가 다시 잡은 Worker의 결과를 덮어쓰지 않습니다.
  - `mpay_`로 시작하는 결제 ID는 멤버십 분기(`MembershipWebhookReconciliationFacade`)로 보내고, 부분 취소 웹훅은 PortOne 조회 없이 `MANUAL_REVIEW`로 넘깁니다.
  - 확정 API: [PaymentConfirmFacade](../../src/main/java/com/recipick/domain/payment/facade/PaymentConfirmFacade.java)는 소유자·결제 ID를 확인한 뒤 PortOne을 조회하고, 잠금 아래 판정은 Processor가 한 번만 합니다. 남의 주문·틀린 결제 ID는 모두 `PAYMENT-001`로 답합니다.
- 코드 근거: `PaymentReconciliationFacade.reconcile`·`confirm`, `WebhookEventService.claim`·`findOwnedForUpdate`, `PaymentExpirationFacade.checkPortOne`.

### 3-2. Redis Sorted Set + Lua 지연 큐와 DB 기준 Fallback (D2)

- 선택: [RedisDelayQueue](../../src/main/java/com/recipick/infra/redis/RedisDelayQueue.java)는 Sorted Set 하나(`payment:delay-queue`)에 member = 작업, score = 실행 시각을 둡니다.
  - 신규 등록 `enqueueIfEarlier`는 Lua로 `ZADD ... LT`를 실행해 이미 잡힌 더 이른 예약을 늦추지 않습니다. Worker가 "N분 뒤 다시"로 정한 경우만 `reschedule`로 미룹니다.
  - `popDue`는 `ZRANGEBYSCORE`와 제거를 한 Lua 스크립트에서 실행해 두 Worker가 같은 작업을 꺼내지 못합니다.
  - [PaymentDelayQueueWorker](../../src/main/java/com/recipick/domain/payment/scheduler/PaymentDelayQueueWorker.java)는 1초 `fixedDelay`로 최대 20건을 꺼내 작업 종류별 Facade로 넘깁니다. 작업 종류 라우팅은 `default` 없는 switch 식이라 종류를 추가하고 빠뜨리면 컴파일이 실패합니다.
  - [PaymentFallbackScheduler](../../src/main/java/com/recipick/domain/payment/scheduler/PaymentFallbackScheduler.java)는 1시간마다(기동 1분 뒤 첫 실행) DB에서 "정상이라면 이미 실행됐어야 할" 미완료 행의 ID만 keyset으로 읽어 큐에 다시 넣습니다. 처리는 하지 않아 실행 경로를 Worker 하나로 유지합니다.
- 선택한 이유: 결제마다 실행 시각이 다르고(5분 만료, 1분 재조회, 5분·10분 재확인), 꺼낸 뒤 장애로 작업이 사라져도 DB 상태가 남으면 언제든 복구할 수 있어야 합니다. 브로커를 새로 들이지 않고 이미 쓰는 Redis를 실행 신호로만 씁니다.
- 큐 등록은 항상 DB 커밋 뒤에 합니다. 결제 생성은 `PaymentCreatedEvent`를 `AFTER_COMMIT` 리스너([PaymentExpirationScheduleListener](../../src/main/java/com/recipick/domain/payment/event/PaymentExpirationScheduleListener.java))가 받아 예약하고, 롤백되면 예약하지 않습니다. 등록 실패(`DataAccessException`)는 로그만 남기고 응답을 바꾸지 않습니다. 행이 미완료 상태로 남아 Fallback이 다시 넣습니다.

| 작업 | 고빈도 재시도 | 고빈도를 멈추는 기준 | Fallback 재등록 대상 |
| --- | --- | --- | --- |
| 웹훅 처리 | 1 → 2 → 4 → 8분, 최대 5회 뒤 `MANUAL_REVIEW` | 시도 횟수 | 1분 넘은 `RECEIVED`, 10분 넘은 `RETRY_PENDING`, 10분 넘은 `PROCESSING` |
| 결제 만료 | 1분 | 만료 시각 + 10분(`STALLED`) | 만료 + 10분이 지난 `IN_PROGRESS` 결제 |
| 회원 환불 재확인 | 첫 5분, 이후 10분 | 요청 + 3시간 | 5분 넘은 회원 `REQUESTED` 환불 |
| SYSTEM·마트 거절 환불 실행 | 10분 | 요청 + 3시간 | 10분 넘은 `REQUESTED` 환불 |

멤버십 환불 실행은 같은 큐를 쓰지만 Fallback 대신 `MembershipRefundRecoveryFacade`의 1분 DB 스캔이 복구합니다(D6).

### 3-3. 외부 PG 호출을 DB 트랜잭션 밖으로 (D3)

- 선택: 진입점 Facade에는 트랜잭션을 걸지 않고, 상태 변경은 `@Transactional` Processor가 맡습니다.

```text
결제 후 회원 취소 (PaymentCancelFacade)
TX1 PaymentCancelProcessor.prepare   PAYMENT 잠금 → 소유 확인 → 같은 키 조회 → 주문 CANCEL_REQUESTED·결제 CANCEL_REQUESTED·REFUND REQUESTED → 커밋
     PortOne 전액 취소                잠금 없음. 멱등 키 recipick-refund-{환불 ID}
TX2 PaymentCancelProcessor.apply     PAYMENT 잠금 → 환불 다시 읽기 → 성공·실패·불명 반영 → 커밋
     예외 변환                         실패 outcome의 예외는 TX2 커밋 뒤 Facade가 던짐
```

- 선택한 이유: PG 응답을 기다리는 동안 같은 결제의 다른 요청이 잠금에 묶이지 않고, 호출 중 서버가 죽어도 TX1의 환불 행이 재조회·재시도의 근거로 남습니다.
- 적용한 구조·실패 처리
  - 업무 결과는 예외가 아니라 반환값(`PaymentCancelOutcome`·`PaymentApprovalOutcome`·`MartRejectionOutcome`)입니다. Processor 안에서 던지면 `PAID` 복귀·환불 `FAILED` 기록까지 롤백되기 때문입니다. HTTP 응답은 커밋 뒤 Facade·Controller가 정합니다(취소 `PENDING`은 202).
  - 결제 후 취소의 포인트·재고는 TX1에서 건드리지 않고 PG 환불이 성공한 TX2에서만 되돌립니다. 먼저 돌려주면 환불 실패로 `PAID`가 돌아와도 포인트만 돌아간 채 남습니다.
  - 마트 거절([MartRejectionFacade](../../src/main/java/com/recipick/domain/payment/facade/MartRejectionFacade.java))도 같은 TX1 → TX2 구조지만, PortOne 호출과 TX2는 Worker·Fallback과 같은 실행기 `AdminRefundExecutionFacade`가 맡고, 실행 전에 10분 안전망 작업을 먼저 등록합니다.
  - 확정·만료·SYSTEM 환불·웹훅도 "스냅샷 읽기 → PortOne(잠금 없음) → Processor(PAYMENT 잠금)" 순서입니다. SYSTEM 환불 실행 예약은 Processor 커밋 뒤 `SystemRefundExecutionFacade.scheduleAfterConfirm`이 합니다. 트랜잭션 안에서 예약하면 롤백 시 없는 환불의 작업이 남고, 커밋 전에 Worker가 꺼내면 아직 보이지 않습니다.
  - 멤버십의 비트랜잭션 진입점(`MembershipSubscriptionFacade`·`MembershipChargeExecutor`·`MembershipRefundExecutionFacade` 등)은 `Propagation.NEVER`로 바깥 트랜잭션 안에서의 호출을 PG 요청 전에 거절합니다.
- 코드 근거: [PaymentCancelFacade](../../src/main/java/com/recipick/domain/payment/facade/PaymentCancelFacade.java), [PaymentCancelProcessor](../../src/main/java/com/recipick/domain/payment/facade/PaymentCancelProcessor.java), [PaymentConfirmProcessor](../../src/main/java/com/recipick/domain/payment/facade/PaymentConfirmProcessor.java), [MartRejectionProcessor](../../src/main/java/com/recipick/domain/payment/facade/MartRejectionProcessor.java).

### 3-4. 비관적 락·UNIQUE·멱등 키의 역할 분담 (D4)

세 수단은 서로를 대신하지 않고 막는 경합이 다릅니다.

| 수단 | 막는 경합 | 대표 적용 |
| --- | --- | --- |
| 비관적 락 + 상태 재검사 | 같은 기존 행을 두고 겹치는 요청 | `PaymentRepository.findByIdForUpdate`·`findByOrderIdForUpdate`로 결제를 먼저 잠근 뒤 상태로 판정 |
| UNIQUE 제약 | 서로 다른 행을 잠가 둘 다 통과한 신규 INSERT | `refund.idempotency_key`, `webhook_event.webhook_id`(`uk_webhook_event_webhook_id`), `refund.portone_cancellation_id`, `payment.order_id`·`portone_payment_id`, `point_earning.order_id`, `point_usage(point_earning_id, order_id)`, `member_point.member_id`, `billing_key.portone_billing_key` |
| 업무 행에 저장한 멱등 키 | 같은 요청의 재전송·연타 | 회원 취소·마트 거절의 `Idempotency-Key`(최대 64자)를 환불 행에 저장. PortOne에는 환불 행마다 고정된 서버 키 |

- 잠금 순서: 상품 결제는 `PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고`, 같은 종류 여러 행은 PK 오름차순(`findAllByIdInForUpdate ... ORDER BY p.id`)입니다([컨벤션](../convention.md#트랜잭션동시성)).
  - 환불 행은 따로 잠그지 않습니다. 환불 변경은 모두 결제 잠금 아래에서 하므로 `PaymentService.getRefundById`는 일반 조회입니다.
  - 탈퇴 정산은 `PAYMENT(PK 오름차순) → REFUND → WEBHOOK_EVENT`를 잠금 읽기로 봅니다. 환불·웹훅은 `PESSIMISTIC_READ`(FOR SHARE)로 최신 커밋을 읽어 회원 잠금 뒤 커밋된 늦은 승인 환불을 놓치지 않습니다.
  - 멤버십은 `MEMBER → BILLING_KEY → MEMBERSHIP → 회차 PK → 시도 PK 오름차순 → 환불 PK 오름차순`입니다. 범위 조건 `FOR UPDATE`로 여러 행을 한 번에 잠그지 않고 ID를 일반 조회로 읽은 뒤 한 행씩 잠급니다.
  - 잠그기 전에 엔티티를 읽어 두지 않습니다. 잠금 조회가 영속성 컨텍스트의 이전 인스턴스를 돌려주기 때문입니다(포인트는 적립 건 엔티티 대신 회원 ID 값만 먼저 읽음).
- 참여 메서드는 `Propagation.MANDATORY`입니다. `PaymentCommandService.create`, `PaymentService.requestMemberRefund`·`requestAdminRefund`·`recordExternalRefund`·`findRefundByIdempotencyKey`·`existsUnsettledForWithdrawal`, `PointCommandService`의 예약·확정·복원·적립 확정, 주문의 종료·재고 복구가 바깥 트랜잭션 없이 따로 커밋되지 않게 합니다.
- 회원 취소의 멱등 판정([PaymentCancelProcessor.prepare](../../src/main/java/com/recipick/domain/payment/facade/PaymentCancelProcessor.java))
  - 결제 잠금 → 소유자 확인(멱등 판정보다 먼저, 남의 결제 상태가 응답 차이로 드러나지 않게) → 같은 키 환불 조회.
  - 같은 키인데 요청 주체·결제·사유가 다르면 `COMMON-011`, 같으면 저장된 환불 상태로 답합니다. 실패 종류(`failure_type`)를 저장해 같은 키 재요청이 원래 응답(409·500)을 재현합니다.
  - 다른 키로 이미 `CANCEL_REQUESTED`면 `COMMON-006`입니다.
  - 다른 결제에 같은 키가 동시에 오면 서로 다른 결제를 잠가 둘 다 통과하므로 `saveAndFlush`의 UNIQUE 위반(`DataIntegrityViolationException`)을 `COMMON-011`로 바꿉니다.
- PortOne 멱등 키: 회원 취소·SYSTEM 환불·마트 거절 환불 모두 `recipick-refund-{환불 ID}`를 보냅니다. 클라이언트 키의 길이·문자 제약이 PortOne으로 새지 않고, 같은 환불을 다른 경로가 다시 보내도 PG에서 한 건입니다.
  - 같은 키 재요청이 PortOne을 다시 부르는 것은 취소 ID가 없고 요청 시각 + 3시간(`PORTONE_IDEMPOTENCY_WINDOW`) 안일 때뿐입니다. 3시간은 PortOne의 같은 키 같은 응답 보장 시간으로 코드에 적힌 외부 정책값입니다.
  - 멤버십 환불은 `pg_idempotency_key`(`mrf_v1_` + UUID, UNIQUE)를 쓰고 최초 발송 + 2시간 55분 미만에서만 같은 키로 재POST합니다.
- 웹훅 inbox: 저장은 짧은 트랜잭션 하나이고, UNIQUE 충돌 판정은 트랜잭션 밖 Facade에서 합니다. 안에서 잡으면 rollback-only로 커밋이 실패하기 때문입니다. 충돌은 "이미 받은 웹훅"이라 200(`DUPLICATE`)입니다. 제약 이름으로 판정하고 이름이 다르면 `webhook_id`로 한 번 더 확인합니다.
- 포인트: 예약(주문 생성) → 확정(결제 성공 `confirmUsage`) → 복원(실패·만료·결제 전 취소·환불 성공 `restoreUsageAndCancelEarning`) → 적립 확정(배송완료 `confirmEarning`, 1년 만료) 순서입니다.
  - 회원 포인트 행이 없으면 일반 조회로 확인한 뒤에만 `INSERT ... ON DUPLICATE KEY UPDATE`로 만들고 `member_id` UNIQUE로 잠급니다. 매번 INSERT를 시도하면 PK 끝 빈 구간 잠금으로 모든 회원의 포인트 변경이 한 줄로 서기 때문입니다.
  - 만료는 매일 00:00(KST) [PointExpirationScheduler](../../src/main/java/com/recipick/domain/payment/scheduler/PointExpirationScheduler.java)가 회원마다 새 트랜잭션으로 처리합니다. 분산 락 없이 `MEMBER_POINT → POINT_EARNING(id 오름차순)` 잠금 뒤 상태를 다시 봐 늦게 온 서버는 0을 돌려줍니다. 포인트를 쓰기 직전에도 같은 코드로 지연 만료를 먼저 실행합니다.
  - 적립 예정 생성은 없는 행을 `FOR UPDATE`로 찾으면 빈 구간 잠금으로 동시 주문끼리 막힐 수 있어 일반 조회 + `order_id` UNIQUE로 방어합니다.
- 코드 근거: [PaymentService](../../src/main/java/com/recipick/domain/payment/service/PaymentService.java), [PaymentRepository](../../src/main/java/com/recipick/domain/payment/repository/PaymentRepository.java), [RefundRepository](../../src/main/java/com/recipick/domain/payment/repository/RefundRepository.java), [WebhookEventService](../../src/main/java/com/recipick/domain/payment/service/WebhookEventService.java), [PointCommandService](../../src/main/java/com/recipick/domain/payment/service/PointCommandService.java), [Refund](../../src/main/java/com/recipick/domain/payment/entity/Refund.java), [WebhookEvent](../../src/main/java/com/recipick/domain/payment/entity/WebhookEvent.java).

### 3-5. 취소·환불·늦은 승인과 SYSTEM 환불 (D5)

- 승인 판정: [PaymentVerifier](../../src/main/java/com/recipick/domain/payment/service/PaymentVerifier.java)는 상태를 바꾸지 않고 판정만 돌려줍니다. 집행은 [PaymentConfirmProcessor](../../src/main/java/com/recipick/domain/payment/facade/PaymentConfirmProcessor.java)가 결제 잠금 아래에서 합니다.

```text
결제 PAID                              → ALREADY_PAID
결제 FAILED + PG PAID(늦은 승인)        → 주문 ID·KRW·PG 금액 존재를 먼저 확인
결제 FAILED·CANCELED                   → ALREADY_CLOSED
결제 IN_PROGRESS가 아님                 → INVALID_STATUS
PG FAILED                              → PG_FAILED
PG CANCELED·PARTIAL_CANCELED           → INVALID_STATUS (상태 변경 없음)
PG가 PAID가 아님                        → NOT_PAID
customData 주문 ID 불일치               → ORDER_MISMATCH
통화 누락 → NOT_PAID, KRW가 아님        → CURRENCY_MISMATCH
금액 누락 → NOT_PAID, PAYMENT.amount와 다름 → AMOUNT_MISMATCH
완료 시각 누락 → NOT_PAID, 만료 시각 초과 → EXPIRED
그 밖                                   → APPROVED
```

- 선택한 이유
  - 금액은 주문 금액이 아니라 결제 생성 때 찍은 `PAYMENT.amount` 스냅샷과 비교하고, 만료는 서버 시각이 아니라 PG가 확인한 결제 완료 시각(`paidAt`)으로 봅니다.
  - 근거가 비어 있으면(금액·완료 시각·통화 없음) 불일치가 아니라 `NOT_PAID`입니다. `AMOUNT_MISMATCH`·`EXPIRED`는 주문 종료·환불을 집행하므로 근거 없는 값으로 집행하지 않습니다.
  - 원화가 아니면 승인 근거도 환불 금액 근거도 아니라서 아무것도 바꾸지 않고 운영 확인(`PAYMENT-013`, ERROR 로그)으로 넘깁니다.
- 판정 소비: `SystemRefundExecutionFacade.scheduleAfterConfirm`, `PaymentReconciliationFacade.confirm`, `PaymentExpirationFacade.applyConfirmResult`는 `default` 없는 값을 받는 switch 식이라 판정 값이 늘면 컴파일이 실패합니다. 상태를 바꾸는 `PaymentConfirmProcessor.process`는 switch 문에 `default -> { }`가 있어 새 값이 무변경으로 지나갈 수 있습니다(4절).
- 늦은 승인·금액 불일치: 주문을 되살리지 않습니다. 결제는 `FAILED`, 주문은 닫힌 채 두고 `requestSystemRefund`가 PG 실제 수납액으로 SYSTEM 환불을 만듭니다. 한 결제의 SYSTEM 환불은 최대 한 건이라 재전송·재조회로 다시 와도 기존 행을 돌려줍니다. PG 금액을 모르면 결제 금액으로 대신 적지 않고 기록을 보류합니다.
- SYSTEM·마트 거절 환불 실행: POST 전에 항상 GET으로 이 환불의 취소가 이미 접수됐는지 봅니다. 콘솔에서 이미 돌려준 전액 취소가 있으면 POST하지 않고 그 취소로 완료합니다.
- 결과 불명: PortOne 응답이 404·상태 불일치 409·timeout·5xx·해석 불가면 `MISMATCH`·`UNCONFIRMED`로 보고 `CANCEL_REQUESTED`·`REQUESTED`를 그대로 둡니다(`PENDING`, 202). `PAID` 복귀는 PortOne이 `FAILED`를 명시했거나 처리 전에 거절(400·401·403)한 경우뿐입니다. 웹훅만 기다리지 않고 환불 재확인을 예약합니다.
- 외부 취소: [RefundCancellationRules](../../src/main/java/com/recipick/domain/payment/facade/RefundCancellationRules.java)가 회원 재확인·웹훅·SYSTEM·마트 거절 실행의 공통 판정입니다.
  - 자동 반영은 콘솔·PortOne 관리자 화면 취소(`CONSOLE`·`PORTONE_ADMIN`)이고 전액·완료 시각 있음·다른 성공 취소 없음·서버 API 취소 처리 중 아님·PG 결제 `CANCELED`일 때만입니다. 차지백·알 수 없는 경로·부분 취소는 운영 확인입니다.
  - 기존 회원·SYSTEM·마트 거절 환불이 있으면 새 `EXTERNAL` 환불을 만들지 않고 그 환불을 끝냅니다. 대응하는 환불이 없고 결제가 `PAID`일 때만 [ExternalCancellationProcessor](../../src/main/java/com/recipick/domain/payment/facade/ExternalCancellationProcessor.java)가 결제 `CANCELED`·`EXTERNAL` 환불·주문 `REFUNDED`·포인트·재고를 한 트랜잭션에서 반영합니다.
  - 포인트는 `restoreForExternalCancel`이 예외 대신 결과를 돌려줍니다. PG에서 이미 돈이 돌아갔으므로 포인트 때문에 정합화를 롤백하지 않고, 적립이 확정된 주문은 회수하지 않은 채 ERROR 로그로 운영 확인을 남깁니다.
- 취소 ID 선택: 취소 ID를 아직 못 받은 환불이 PortOne 취소 중 자기 것을 고를 때, 이 환불의 것이라고 확신할 수 없는 취소가 하나라도 살아 있으면 어느 것도 고르지 않습니다. 잘못 묶으면 되돌릴 수 없고 새로 요청하면 같은 돈을 두 번 돌려줄 수 있기 때문입니다.

### 3-6. 자동결제 선점·결과 불명 재조회·강한 정합성 차단 (D6)

세부 계약은 [멤버십 첫 결제](../api/membership-first-charge.md)·[갱신](../api/membership-renewal.md)·[이용료 환불](../api/membership-refund.md)·[정합화](../api/membership-reconciliation.md) 문서에 있고, 여기서는 정합성 결정만 적습니다.

- 회차·시도 분리: 회차(`membership_payment`)는 `UNIQUE(membership_id, period_start_date)`와 구독 `idempotency_key` UNIQUE, 시도(`membership_payment_attempt`)는 `portone_payment_id` UNIQUE와 `UNIQUE(membership_payment_id, attempt_no)`입니다. 시도의 PortOne 결제 ID(`mpay_` + UUID)는 접수 TX1에서 만들어 저장합니다.
- 선점: [MembershipChargeDispatchProcessor](../../src/main/java/com/recipick/domain/payment/facade/MembershipChargeDispatchProcessor.java)는 시도 PK 한 행만 잠그고 `READY → DISPATCHING`만 바꿉니다. 회원·이용권은 잠그지 않습니다. 여러 서버가 같은 `READY`를 집어도 잠금 뒤 상태 검사로 하나만 성공합니다. 임대(`lease` 기본 10분)와 여유(`dispatch-margin` 2분) 안의 `DISPATCHING`이면 다른 Worker가 PG 호출 중으로 보고 GET·POST 없이 `IN_PROGRESS`를 돌려줍니다.
- 청구: [MembershipChargeExecutor](../../src/main/java/com/recipick/domain/payment/facade/MembershipChargeExecutor.java)는 선점 → 기능 스위치·접수 유효 창·회차 차단 재확인 → PG POST → 같은 결제 ID GET → TX2 순입니다. POST 응답은 참고일 뿐 확정은 GET으로만 합니다. 어떤 경우에도 새 결제 ID로 다시 POST하지 않습니다.
- 결과 반영 TX2([MembershipChargeResultProcessor](../../src/main/java/com/recipick/domain/payment/facade/MembershipChargeResultProcessor.java)): `MEMBER → 이용권 → 회차 PK → 시도 PK 오름차순`으로 잠근 뒤 판정 시각을 다시 읽습니다. 결과 기록과 이용권 반영을 한 트랜잭션으로 커밋해 회원 쪽 반영이 실패하면 결제 기록도 롤백되고 복구가 같은 결제 ID로 다시 합니다.
- 결과 불명 창: 발송 시각 + `unknown-window`(기본 24시간)까지는 `UNKNOWN`으로 두고 같은 ID로 재조회합니다. 창이 끝나면 이번 GET이 마지막 자동 조회이고 시도·회차를 `MANUAL_REVIEW`로 바꿔 운영 확인 표시를 남깁니다. 기준은 발송 시각 하나이며 중간 조회로 늘어나지 않습니다. 이후에도 웹훅·운영자 재조회는 받습니다.
- 강한 정합성 차단: TX2가 명시적으로 판정한 정합성 오류(`MembershipSettlementInvariantException`)는 전체 롤백 뒤 실행기가 별도 트랜잭션으로 원인을 다시 확인해 시도에 차단 표시를 남깁니다. 차단 시도가 있는 회차는 선점·자동 재조회·결과 반영에서 모두 빠지고 운영자 재조회만 관측값을 돌려줍니다. PG에 보낸 적 없는 시도에 PG 거래가 보이는 경우도 이 차단 대상입니다.
- 기능 스위치: `recipick.membership-billing.enabled`(운영 기본 `false`)는 새 청구(신규 접수·READY 재접수·PG POST)만 막고, 이미 생긴 결제의 재조회·반영·복구·환불은 계속 돌립니다. 갱신 재시도 간격 24시간·회차당 발송 3회는 설정이 달라도 따르지 않고 기동을 멈추는 정책값입니다.
- 복구: `MembershipBillingScheduledTasks`가 청구 복구·갱신 정리는 5분, 환불 복구는 1분마다 실행합니다. 분산 락 없이 회원 잠금·재확인·회차 UNIQUE·선점으로 한 건만 처리합니다.
- 빌링키 등록·교체: [BillingKeyRegistrationFacade](../../src/main/java/com/recipick/domain/payment/facade/BillingKeyRegistrationFacade.java)는 PortOne 검증을 잠금 밖에서 끝내고, [BillingKeyRegistrationProcessor](../../src/main/java/com/recipick/domain/payment/facade/BillingKeyRegistrationProcessor.java)가 회원 잠금을 첫 DB 작업으로 등록·교체를 직렬화합니다. 두 회원이 같은 키를 등록하면 `uk_billing_key_portone_billing_key`가 한 명만 통과시켜 `BILLING-002`가 됩니다.

## 4. 트레이드오프

- 얻는 점
  - PG를 기다리는 동안 DB 잠금을 쥐지 않고, 호출 중 서버가 죽어도 접수 행·미완료 상태가 DB에 남아 Worker·Fallback·복구 스캔이 이어받습니다.
  - 확정 API·웹훅·만료 작업이 같은 조회·같은 Processor·같은 잠금으로 수렴해 반복·동시 전달이 한 번의 반영으로 끝납니다.
  - "모름"을 "실패"로 처리하지 않아 돈과 주문이 어긋나는 방향의 자동 처리를 하지 않습니다.
  - Redis가 비거나 꺼져도 정합성은 DB가 지킵니다. Redis 장애의 비용은 처리 지연(최대 Fallback 주기 1시간)입니다.
- 감수하는 비용·해결하지 못한 부분
  - 처리마다 PortOne 조회가 1회 늘고, 결과 불명 건은 고빈도 재조회 뒤 Fallback의 1시간 주기로 넘어가 확정이 늦어질 수 있습니다.
  - 같은 결제를 TX1·TX2로 두 번 잠그고, TX2는 그 사이 웹훅·다른 키 요청이 끝낸 경우를 모두 판정해야 해 분기가 많습니다.
  - `MANUAL_REVIEW`·`STALLED`·멤버십 강한 차단은 사람의 확인을 전제로 합니다. 운영 알림은 ERROR 로그에 기대며 별도 알림 채널은 이 범위에 없습니다.
  - `PaymentConfirmProcessor.process`의 switch 문에는 `default -> { }`가 있어, 판정 값을 추가했을 때 집행 누락을 컴파일러가 잡지 못합니다. 소비 쪽 switch 식 세 곳이 컴파일 오류로 알려 주는 것에 기대고 있습니다.
  - 교착(SQLState 40001)·잠금 대기 초과는 결제 도메인 코드에서 업무 오류로 바꾸지 않습니다. HTTP 경계에서는 [GlobalExceptionHandler](../../src/main/java/com/recipick/global/exception/GlobalExceptionHandler.java)가 `PessimisticLockingFailureException` 계열을 `409 COMMON-010`으로 답하고, 큐 Worker 경로는 `TransientDataAccessException`으로 받아 재시도합니다. 같은 키 경합이 교착으로 끝나는 경우의 응답은 `COMMON-011`이 아니라 `COMMON-010`이 됩니다(#437, [동시성 문서 6-4](../concurrency.md)).
  - 3시간·2시간 55분 같은 PortOne 멱등 보장 시간은 외부 정책값이라 PortOne이 바꾸면 코드를 함께 고쳐야 합니다.
  - PG가 `CANCELED`인데 내부 결제가 `IN_PROGRESS`인 경우는 테스트 채널 확인 전 임시 판정(`INVALID_STATUS`, 상태 변경 없음)입니다.
- 다시 검토할 조건
  - Fallback 주기(1시간) 동안의 지연이 회원 응대에 문제가 되면 주기 단축이나 재조회 구간 확대를 검토합니다.
  - `MANUAL_REVIEW` 건이 운영으로 처리하기 어려울 만큼 쌓이면 관리자 재처리 API·알림을 우선 추가합니다.
  - #437의 원인이 교착으로 확인되면 같은 키 경합의 교착을 업무 오류로 바꿀지, 잠금·쿼리 순서를 바꿀지 정합니다.
  - 부분 취소를 정책으로 열면 외부 취소 판정과 SYSTEM 환불 금액 기준을 다시 정합니다.

## 5. 결과

검증 결과는 [동시성 문서 6절](../concurrency.md)에 시나리오별로 모았습니다. 아래는 결정과 검증 클래스의 연결입니다.

| 검증 항목 | 방법·환경 | 기대 결과 | 실제 결과 | 근거 |
| --- | --- | --- | --- | --- |
| D1 웹훅·확정 수렴 | `PaymentWebhookConcurrencyTest`, `WebhookFlowIntegrationTest`(실제 MySQL·Redis) | 같은 `webhook_id` 행 1개, 확정 API와 웹훅이 겹쳐도 승인 1회 | 통과 | [동시성 6-5](../concurrency.md) |
| D2 큐 유실 복구 | `PaymentRecoveryIntegrationTest`, `PaymentExpirationFlowIntegrationTest`, `SystemRefundFlowIntegrationTest`(실제 MySQL·Redis), `PaymentFallbackSchedulerTest`·`PaymentDelayQueueWorkerTest`(Mockito 단위) | 큐에서 사라진 작업을 Fallback이 다시 넣어 최종 처리 | 통과 | 같은 절 |
| D3 TX1·TX2 경계 | `PaymentCancelFacadeIntegrationTest`, `RefundWebhookConcurrencyTest`, `PaymentPointIntegrationTest` | PG 실패여도 `PAID` 복귀·환불 `FAILED`가 남음, 포인트는 TX2에서만 복원 | 통과 | 같은 절 |
| D4 잠금·UNIQUE·멱등 키 | `PaymentCancelAfterPayConcurrencyTest`, `PaymentConfirmConcurrencyTest`, `MartRejectionConcurrencyTest`, `PointReservationConcurrencyTest`, `PointExpirationConcurrencyTest` | 환불 행 1건·PortOne 취소 1건·재고 1회 복구·잔액 음수 없음 | 통과(#437 간헐 실패 별도) | 같은 절 |
| D5 늦은 승인·외부 취소 | `PaymentConfirmConcurrencyTest`, `PaymentCurrencyMismatchIntegrationTest`, `ExternalCancellationConcurrencyTest`·`ExternalCancellationFlowIntegrationTest`, `PaymentVerifierTest`(단위) | SYSTEM 환불 1건, 원화 아님은 무변경, `EXTERNAL` 환불 1회 | 통과 | 같은 절 |
| D6 자동결제 | `MembershipChargeConcurrencyTest`, `MembershipRenewalConcurrencyTest`, `MembershipReconciliationRecoveryIntegrationTest`, `MembershipRefundConcurrencyTest`, `BillingKeyRegistrationConcurrencyTest` | 회차·시도 1건, POST 1회, 결과 불명 창 24시간 정확 | 통과 | 같은 절 |
| 결제 패키지 전체 | `./gradlew test --tests 'com.recipick.domain.payment.*'`, 작성자 작업 환경(로컬 MySQL 8.0 + Redis, 아래 설명) | 전체 통과 | 136개 클래스 1,673개, 실패 0·오류 0·건너뜀 0 | 작성자 로컬 실행 2026-11-04(`9172f6d`), Gradle 테스트 결과 XML 기준. 재현 명령과 CI 확인은 [동시성 문서 6-5절](../concurrency.md#6-5-실제-동시-요청실패-결과) |

실행 환경: Docker를 쓸 수 없는 작성자 작업 환경이라 `IntegrationTestSupport`의 Testcontainers 연결만 로컬 MySQL 8.0(`innodb_lock_wait_timeout=5`)·Redis로 바꿔 실행한 결과입니다. 이 연결 변경은 커밋하지 않았습니다. 저장소 기본 구성(CI 포함)은 Testcontainers MySQL 8.4(`--innodb-lock-wait-timeout=5`)·Redis 7.4이며, 이 PR의 CI `test` 작업이 같은 범위를 그 환경에서 다시 실행합니다. 두 환경의 결과를 같은 것으로 보지 않습니다.
여러 앱 인스턴스·실 PortOne 호출·부하 테스트를 한 결과는 아닙니다.

## 6. 참고 자료

- [요구사항](../requirement.md), [컨벤션](../convention.md)의 트랜잭션·동시성 절, [ERD](../erd.dbml)의 `PAYMENT`·`REFUND`·`WEBHOOK_EVENT`·`POINT_*`·`MEMBERSHIP_PAYMENT`·`MEMBERSHIP_REFUND`·`BILLING_KEY`
- [주문↔결제 연동 기준](../order-payment-integration.md), [장바구니·주문 ADR](cart-order.md)의 D11·D12, [스케줄 작업 정합성](../scheduler-consistency.md), [동시성 전략](../concurrency.md)
- PortOne V2 공식 문서의 결제 취소 API·웹훅·멱등 키 절(링크 확인 필요. 3시간 멱등 보장·30초 재전송은 코드 주석 기준)
- [MySQL InnoDB 잠금](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html), [Spring 트랜잭션 전파](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html), [Redis ZADD](https://redis.io/docs/latest/commands/zadd/)
