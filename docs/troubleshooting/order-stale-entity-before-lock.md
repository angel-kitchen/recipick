# 잠금 조회 전에 읽은 엔티티 때문에 다른 트랜잭션의 변경을 덮어씀

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 잠금 조회가 이전 값을 돌려줌 · 준희 · 2026-10-22(주문 생성), 2026-10-28(결제 Facade 주문 조회) |
| 영향 범위 | 주문 생성의 재고 차감, 같은 트랜잭션에서 주문 ID 조회 뒤 주문을 잠그는 결제 쪽 준비 단계(회원 취소 `PaymentCancelProcessor.prepare`, 마트 거절 `MartRejectionProcessor.prepare`). 결제 확정은 트랜잭션 없는 `PaymentConfirmFacade`에서 따로 조회해 해당하지 않음. 개발 중 발견해 운영 데이터 영향은 없음 |
| 관련 이슈·PR·커밋 | #108(PR #127, 값 조회 추가 `c502b2c`, 주문 생성 적용 `b3c6a5d`), #238(PR #239, `69cde02`) |
| 처리 상태 | 해결. 상품 재고 쪽 회귀 테스트는 후속 작업 |

## 1. 증상

- 기대한 동작과 실제 동작
  - 기대: `PESSIMISTIC_WRITE`로 잠근 뒤 읽은 값은 그 시점에 커밋된 최신 값이다. 그 값으로 검증하고 변경하면 다른 트랜잭션의 변경을 잃지 않는다.
  - 실제: 같은 트랜잭션에서 같은 행을 잠금 없이 먼저 읽어 두면, 뒤의 잠금 조회는 잠금을 잡고도 먼저 읽은 이전 값을 돌려줬다. 그 값으로 계산한 결과가 다른 트랜잭션의 변경을 덮어썼다.
- 발생 환경·버전·재현 조건
  - Spring Boot 3, Spring Data JPA(Hibernate), MySQL 8 InnoDB(REPEATABLE READ), Testcontainers
  - 한 트랜잭션 안에서 `findById`처럼 잠금 없이 엔티티를 읽은 뒤, 같은 행을 `findByIdForUpdate`로 다시 읽을 때 발생한다.
- 재현 순서(주문 생성, #108 개발 중 임시 테스트)
  1. 주문 생성 트랜잭션이 "이전 마트 상품이 섞였는지" 확인하려고 장바구니 상품 엔티티를 잠금 없이 읽는다(`findProducts`). 이때 재고는 10이다.
  2. 다른 트랜잭션 3개가 같은 상품 재고를 1씩 차감하고 커밋한다. DB 재고는 7이 된다.
  3. 주문 생성 트랜잭션이 `decreaseStocks`에서 상품을 `FOR UPDATE`로 잠그고 1을 차감한다.
- 핵심 관찰 결과
  - 기대 재고는 6인데 실제로는 9였다. 다른 트랜잭션 3건의 차감이 사라졌다.
  - #238에서 같은 구조를 다시 발견했다. 회원 취소·마트 거절의 결제 쪽 준비 트랜잭션이 `OrderService.getMemberIdOf`·`getMartIdOf`로 주문 엔티티를 먼저 읽고 같은 트랜잭션에서 주문을 잠그면, 그 사이 커밋된 마트 수락(`RECEIVED → PREPARING`)을 보지 못하고 덮어쓸 수 있었다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 잠금이 걸리지 않아 동시에 차감한다 | 재고 차감 경로의 조회 메서드 확인 | `decreaseStocks`는 `PESSIMISTIC_WRITE` 조회(`findAllByIdInForUpdate`)를 쓴다. 잠금 누락은 아니다. 기각 |
| REPEATABLE READ 스냅샷 때문에 이전 값을 읽는다 | InnoDB 문서의 잠금 읽기 규칙 | `FOR UPDATE`는 스냅샷이 아니라 최신 커밋 값을 읽는 잠금 읽기다. DB가 이전 값을 줄 이유는 없다. 기각 |
| Hibernate가 DB 결과 대신 영속성 컨텍스트의 엔티티를 돌려준다 | 먼저 읽는 조회를 엔티티 대신 값만 읽는 조회로 바꿔 같은 순서로 다시 실행 | 값만 읽으면 재고가 6이 됐다. 채택 |

가설 1·2는 코드와 문서로 판단했고, 실제로 다시 실행해 확인한 것은 가설 3이다.

확인된 원인

- Hibernate는 조회 결과 행의 식별자가 이미 영속성 컨텍스트(1차 캐시)에 있으면, 그 행의 컬럼 값으로 엔티티를 새로 채우지 않고 이미 있는 엔티티를 그대로 돌려준다. 같은 트랜잭션 안에서 엔티티가 하나만 존재하도록 보장하는 동작이다.
- 그래서 `FOR UPDATE`가 DB에서 잠금을 잡고 최신 값(재고 7)을 가져와도, 애플리케이션이 받는 엔티티는 1단계에서 읽은 재고 10 그대로다.
- 그 엔티티에서 1을 빼면 변경 감지가 `stock = 9`로 UPDATE한다. 잠금은 동시에 쓰는 것을 막았지만, 계산의 기준 값이 이미 낡아 있었다.
- 원인은 잠금 방식이 아니라 "잠금 전에 같은 엔티티를 읽었다"는 실행 순서다. 다른 도메인의 Service가 엔티티를 읽는지는 호출하는 쪽에서 보이지 않아서 놓치기 쉬웠다.

## 3. 해결

- 바꾼 내용과 이유
  - 주문 생성(#108): 이전 마트 상품 검증을 `ProductService.findProducts` 대신 `ProductService.findMartIdsOf`로 한다. `select p.id, p.mart.id`처럼 값만 조회해 상품 엔티티를 영속성 컨텍스트에 올리지 않는다. 그래서 뒤이은 `ProductStockService.decreaseStocks`의 잠금 조회가 최신 재고로 엔티티를 만든다.
  - 결제 Facade 조회(#238): `OrderService.getMemberIdOf`·`getMartIdOf`가 `findById`로 주문 엔티티를 읽던 것을 `findMemberIdById`·`findMartIdById` JPQL 값 조회로 바꿨다.
  - 두 곳 모두 코드에 "엔티티를 읽으면 뒤이은 잠금 조회가 이전 상태를 받는다"는 이유를 주석으로 남겼다. 나중에 편해 보이는 `findById`로 되돌리지 않게 하기 위해서다.
- 다른 대안을 선택하지 않은 이유

| 대안 | 선택하지 않은 이유 |
| --- | --- |
| 잠금 조회 뒤 `entityManager.refresh()` | 다른 도메인의 엔티티라 주문 쪽에서 `EntityManager`를 직접 다뤄야 한다. 호출하는 쪽이 매번 기억해야 하는 규칙이 된다. |
| 검증 전에 `entityManager.clear()` | 같은 트랜잭션에서 이미 관리 중인 주문·이력 엔티티까지 분리돼 다른 문제가 생긴다. |
| 처음부터 잠금 조회로 검증 | #108 당시(`b3c6a5d`) 주문 생성은 검증 → 주문 저장 → 재고 잠금 순서였다. 검증에서 상품을 잠그면 재고 잠금이 주문 저장보다 앞서 `ORDERS → 재고` 순서를 어긴다(PR #127 리뷰 포인트). 이후 회원 잠금(#176)과 포인트 예약(#253)이 들어와 지금은 `MEMBER` 잠금 → 주문 저장 → `MEMBER_POINT`·`POINT_EARNING` 잠금 → 재고 잠금 순서이고, 검증에서 상품을 잠그면 포인트 잠금보다도 앞서게 된다. |
| 낙관적 락(`@Version`) | 컨벤션에서 금지한다. |

- 변경 코드
  - `src/main/java/com/recipick/domain/order/facade/OrderFacade.java`의 `validateSameMart`
  - `src/main/java/com/recipick/domain/product/service/ProductService.java`의 `findMartIdsOf`
  - `src/main/java/com/recipick/domain/product/repository/ProductRepository.java`의 `findMartIdsByIdIn`
  - `src/main/java/com/recipick/domain/order/repository/OrderRepository.java`의 `findMemberIdById`·`findMartIdById`
- 기존 데이터·운영에 필요한 조치: 없음. 두 경우 모두 기능을 합치기 전에 발견했다.

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 재고 차감 덮어쓰기(주문 생성) | 임시 테스트: 재고 10, 다른 트랜잭션 3건 차감 뒤 주문 생성 1건 | 재고 9(3건 유실) | 재고 6 | PR #127 리뷰 포인트. 임시 테스트는 커밋하지 않음 |
| 마트 수락 덮어쓰기(주문 조회) | `OrderRepositoryTest.findIdsBeforeLock_lockReadsLatestState`: ID 조회 → 벌크 UPDATE로 다른 트랜잭션 커밋 흉내 → 잠금 조회 | 테스트의 ID 조회를 `findById`로 바꾸면 실패(이전 상태 `RECEIVED`) | 통과(최신 상태 `PREPARING`) | PR #239 테스트 항목 |
| 주문 생성 동시성 회귀 | `OrderCreateConcurrencyTest` 4건(같은 키 동시 주문, 재고 1개 상품 동시 주문 등) | 해당 없음 | 4건 통과 | 2026-11-03 `dev`(`69e7ba3`) 실행 |
| 주문 쪽 리포지토리 회귀 | `OrderRepositoryTest` 21건(위 잠금 조회 테스트 포함) | 해당 없음 | 21건 통과 | 2026-11-03 `dev`(`69e7ba3`) 실행 |

실행 명령: `./gradlew test --tests '*OrderCreateConcurrencyTest' --tests '*OrderRepositoryTest'` (Docker 필요, Testcontainers MySQL)

## 5. 재발 방지와 남은 작업

- 추가한 테스트·문서
  - 주문 쪽: "ID를 먼저 조회해도 잠금 조회가 최신 상태를 읽는다" 리포지토리 테스트. 리포지토리의 값 조회 메서드가 엔티티를 올리지 않는다는 것을 확인한다.
  - 두 조회 메서드에 이유를 적은 주석
- 남은 제한과 담당 이슈
  - 상품 재고 쪽은 같은 수치를 확인하는 회귀 테스트가 커밋되어 있지 않다. 임시 테스트를 `OrderCreateConcurrencyTest`에 정식으로 추가하는 것이 후속 작업이다.
  - 리포지토리 테스트는 리포지토리 메서드를 직접 부르므로, `OrderService.getMemberIdOf`·`getMartIdOf`를 `findById`로 되돌리는 회귀는 잡지 못한다. 서비스를 거쳐 ID 조회 → 다른 트랜잭션 커밋 → 잠금 조회를 확인하는 통합 테스트가 후속 작업이다.
  - 이 문제는 컴파일이나 단위 테스트(Mock)로는 드러나지 않는다. 실제 DB에서 두 트랜잭션을 겹치게 해야 보인다.
- 다른 팀원에게 공유할 적용 방법
  - 한 트랜잭션에서 어떤 행을 잠그고 변경할 예정이면, 잠그기 전에는 그 행의 엔티티를 읽지 않는다. 잠금 전에는 `memberId`·`martId`처럼 바뀌지 않는 식별자만 값 조회(JPQL 프로젝션)로 읽는다. 잠금 없는 조회는 REPEATABLE READ에서 트랜잭션 스냅샷을 읽으므로, 주문·마트 처리 상태처럼 바뀌는 값은 잠금 조회 뒤에 검증한다.
  - 다른 도메인의 Service를 잠금 앞에서 호출할 때는, 그 메서드가 엔티티를 읽는지 확인한다.
  - 리뷰할 때 "`findById` → 같은 ID로 `findByIdForUpdate`" 순서가 한 트랜잭션에 있으면 이 문제를 의심한다.
