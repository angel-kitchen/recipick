# 포인트 예약 시 불필요한 잠금으로 발생한 회원 간 경합 해결

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 포인트 예약의 Gap Lock 경합 · **최정윤** · 2026-10-30(수정일) |
| 영향 범위 | 주문 생성 시 포인트 예약, `MEMBER_POINT`, `POINT_USAGE`, 다른 회원의 동시 주문 |
| 관련 이슈·PR·커밋 | [#253](https://github.com/angel-kitchen/recipick/issues/253) · [PR #278](https://github.com/angel-kitchen/recipick/pull/278) · [`a018d09853`](https://github.com/angel-kitchen/recipick/commit/a018d09853) |
| 처리 상태 | 해결 |

## 1. 증상

- **기대한 동작과 실제 동작:** 동일 회원의 포인트 차감은 직렬화하되 다른 회원의 예약은 독립적으로 처리해야 했다. 초기 구현에서는 다른 회원 사이에도 잠금 대기와 교착 위험이 발생했다.
- **발생 환경·버전·재현 조건:** Spring Boot/JPA, MySQL InnoDB. 서로 다른 회원이 각자 새로운 주문에 포인트를 예약하는 동시 요청.
- **재현 명령·요청·순서:** `PointReservationConcurrencyTest.reservationDoesNotWaitForOtherMembersUncommittedReservation()`에서 한 회원의 예약 트랜잭션을 커밋 전까지 유지한 채 다른 회원의 예약을 실행한다.
- **핵심 오류 로그·관찰 결과:** MySQL 교착은 오류 코드 `1213`, 잠금 대기 초과는 `1205`로 구분한다. `SHOW ENGINE INNODB STATUS\G`에서 `LATEST DETECTED DEADLOCK`과 잠금 대상 인덱스를, `performance_schema.data_lock_waits`에서 대기 관계를 확인한다. 초기 코드에는 기존 포인트 행에 대한 불필요한 INSERT와 빈 사용 내역에 대한 `FOR UPDATE` 조회가 있었다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 잔액 계산 오류 | 잔액과 적립 건 잔량 확인 | 다른 회원 사이 잠금 대기의 직접 원인이 아님 |
| 포인트 행이 있어도 INSERT 실행 | 수정 전 `lockMemberPoint()` 확인 | 매 호출마다 `INSERT ... ON DUPLICATE KEY UPDATE` 실행 · 확인 |
| 없는 주문 사용 내역을 잠금 조회 | 수정 전 `reserveUsage()`와 Repository 확인 | 빈 인덱스 범위에 불필요한 잠금 발생 가능 · 확인 |

확인된 원인: 같은 회원 잔액을 보호하는 잠금과 신규 주문에 아직 존재하지 않는 사용 내역의 잠금을 구분하지 못했다. InnoDB의 인덱스 구간 잠금 때문에 서로 다른 회원도 영향을 받을 수 있었다.

## 3. 해결

- **바꾼 내용과 이유:** `PointCommandService.lockMemberPoint()`에서 `existsByMemberId()`로 존재 여부를 확인하고 없을 때만 행을 생성한다. `reserveUsage()`의 기존 사용 내역 조회를 `findAllByOrderIdOrderByIdAsc()`로 변경했다. 잔액을 수정하는 `MEMBER_POINT`의 `PESSIMISTIC_WRITE`는 유지했다.
- **다른 대안을 선택하지 않은 이유:** 모든 잠금을 제거하면 동일 회원의 잔액 초과 사용을 막지 못한다. 신규 주문의 중복 예약은 주문 생성 단계의 회원 잠금과 멱등 키로 방어한다.
- **변경 코드·설정·문서:** [`PointCommandService`](../../src/main/java/com/recipick/domain/payment/service/PointCommandService.java), [`MemberPointRepository`](../../src/main/java/com/recipick/domain/payment/repository/MemberPointRepository.java), [수정 커밋](https://github.com/angel-kitchen/recipick/commit/a018d09853).
- **기존 데이터·운영에 필요한 조치:** 스키마·데이터 마이그레이션 없음.

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 회원 간 포인트 예약 경합 | `PointReservationConcurrencyTest.reservationDoesNotWaitForOtherMembersUncommittedReservation()` | 빈 구간 잠금으로 다른 회원의 예약도 대기 가능 | 다른 회원의 예약이 선행 미커밋 예약을 기다리지 않고 성공하도록 검증 | [테스트](../../src/test/java/com/recipick/domain/payment/integration/PointReservationConcurrencyTest.java) |
| 같은 회원의 잔액 보호 | `PointReservationConcurrencyTest.onlyOneReservationWinsWithinBalance()` | 과도한 잠금 적용 | 1,000P에서 700P 동시 요청 2건 중 1건만 성공, 최종 300P | [테스트](../../src/test/java/com/recipick/domain/payment/integration/PointReservationConcurrencyTest.java) |
| 최초 포인트 행 생성 경합 | `PointExpirationConcurrencyTest.firstRowIsCreatedOnceUnderContention()` | 매번 INSERT 시도 | 동시 생성 후 포인트 행 1건, 잔액 0 | [테스트](../../src/test/java/com/recipick/domain/payment/integration/PointExpirationConcurrencyTest.java) |

## 5. 재발 방지와 남은 작업

- **추가한 테스트·알림·문서·규칙:** 서로 다른 회원의 동시 예약과 최초 행 생성 테스트를 유지한다. 없는 행을 `FOR UPDATE`로 조회하기 전에 인덱스 잠금 범위를 검토한다.
- **남은 제한과 담당 이슈:** `reserveUsage()`는 주문 생성의 선행 회원 잠금·멱등 계약을 전제로 하므로 독립 진입점으로 사용하지 않는다. 관련 이슈 #253.
- **다른 팀원에게 공유할 적용 방법:** 비관적 락을 무조건 추가하지 말고, 변경 대상 행에만 적용하며 동시성 테스트에서 다른 회원의 처리까지 확인한다.
