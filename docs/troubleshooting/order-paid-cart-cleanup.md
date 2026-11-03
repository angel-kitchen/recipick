# 결제 완료 후 장바구니 정리가 결제 확정을 롤백시킬 수 있었음

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 결제 완료 장바구니 정리의 트랜잭션 분리 · 준희 · 2026-11-02 |
| 영향 범위 | 결제 확정 API·웹훅·만료 재조회·대사·0원 결제의 결제 성공 경로, 회원 장바구니. 기능을 합치기 전 리뷰에서 발견해 운영 데이터 영향은 없음 |
| 관련 이슈·PR·커밋 | #352(PR #353). 처음 시도 `94773de`, 되돌림 `70745c2`, 이벤트 분리 `8d75583`, 수량 차감 `11bdaf9`, 테스트 보강 `767ed88` |
| 처리 상태 | 해결. 정리 실패 시 자동 재처리는 없음(운영 대응으로 처리) |

## 1. 증상

- 기대한 동작과 실제 동작
  - 기대: 결제가 성공하면 결제한 상품이 장바구니에서 빠진다. 결제 실패·만료 때는 다시 결제할 수 있게 장바구니를 남긴다.
  - 실제(기능 추가 전): 결제가 성공해도 결제한 상품이 장바구니에 계속 남았다.
  - 처음 시도(`94773de`)의 문제: 결제 확정 트랜잭션 안에서 장바구니를 지우게 했더니, 장바구니 DELETE가 실패하면 이미 PG에서 승인된 결제의 확정까지 롤백되는 구조가 됐다.
- 발생 환경·재현 조건
  - 처음 시도는 `PaymentConfirmProcessor`의 APPROVED 분기에서 `pointCommandService.confirmUsage(orderId)` 바로 뒤에 `orderFacade.removePaidItemsFromCart(orderId)`를 호출했다. 0원 결제는 `OrderFacade.createOrder`에서 `complete()` 뒤에 같은 메서드를 호출했다.
  - 장바구니 행 잠금 대기 초과(`COMMON-010`)나 DB 오류가 나면 결제 확정 트랜잭션 전체가 롤백된다.
- 핵심 관찰 결과: 결제 담당자에게 확인을 부탁하는 과정에서 아래 세 가지 문제가 드러났다.

| 문제 | 내용 |
| --- | --- |
| 롤백 범위 | 부가 기능 하나 때문에 이미 승인된 결제가 반영되지 않는다. |
| 잠금 순서 | 결제 잠금 순서 `PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고` 뒤에 `CART_ITEM`이 붙는다. 지금은 순환이 없어도 결제 트랜잭션이 잡는 자원이 늘어난다. |
| 도메인 경계 | 결제 코드가 장바구니 정리를 직접 알아야 한다. 결제 성공 경로(확정 API·웹훅·만료 재조회·대사·0원 결제)를 새로 만들 때마다 같은 호출을 잊지 않아야 한다. |

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 장바구니 정리도 결제와 같이 강한 정합성이 필요하다 | 컨벤션의 도메인 간 호출 규칙과 비교 | 강한 정합성이 필요한 것은 주문·결제·재고다. 장바구니가 잠깐 남아 있어도 결제·주문 데이터는 맞다. 기각 |
| 정리 실패가 결제를 롤백시키는 것은 호출 위치 때문이다 | 처음 시도의 트랜잭션 경계 확인 | 같은 트랜잭션에서 호출해서 실패가 함께 롤백된다. 채택 |

확인된 원인

- 일시적으로 어긋나도 되는 후처리(장바구니 정리)를 강한 정합성이 필요한 작업(결제 확정)과 같은 트랜잭션에 넣었다.
- 컨벤션은 "`ApplicationEvent`는 일시적 불일치가 허용되는 후처리에만 쓴다"고 정하는데, 장바구니 정리가 바로 그 경우였다.

리뷰에서 추가로 드러난 문제

- 항목을 통째로 지우면, 결제 대기 중에 같은 상품을 더 담은 수량까지 사라진다.

## 3. 해결

- 바꾼 내용과 이유
  1. `70745c2`에서 결제 코드와 `OrderFacade`에 넣었던 호출을 모두 되돌렸다. 메인 코드 기준으로 `src/main/java/com/recipick/domain/payment/`는 이 기능 전과 같다. 테스트는 결제 흐름에서 검증해야 해서 `src/test/java/com/recipick/domain/payment/integration/`에 `PaymentCartCleanupIntegrationTest`를 추가하고 `PointsOnlyPaymentIntegrationTest`를 고쳤다.
  2. `OrderService.markPaid`가 주문을 PAID로 바꾸는 트랜잭션 안에서 주문 상품을 조회해 `OrderPaidEvent`를 발행한다(`8d75583`). 모든 결제 성공 경로가 `markPaid`를 거치므로, 새 경로가 생겨도 호출을 빠뜨릴 일이 없다.
  3. `PaidCartCleanupListener`가 커밋 뒤에 받아 `CartService.deductPaidItems`를 호출한다.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void removePaidItems(OrderPaidEvent event) { ... }
```

- `AFTER_COMMIT`: 결제 커밋이 성공했을 때만 실행된다. 롤백되면 이벤트도 버려진다.
- `NOT_SUPPORTED`: 이미 끝난 결제 트랜잭션에 묶이지 않고, `deductPaidItems`가 자기 트랜잭션을 새로 연다.
- 예외는 리스너 안에서 잡는다. `DataAccessException`은 WARN, 그 밖의 `RuntimeException`은 ERROR와 스택으로 남기고 호출한 쪽에는 올리지 않는다.

삭제 대신 결제 수량 차감(`11bdaf9`)

1. 수량이 결제 수량 이하인 항목은 삭제한다.
2. 남은 항목은 결제 수량만큼 줄인다.

삭제를 먼저 해야 뺀 결과가 다시 삭제 조건에 걸리지 않는다. 장바구니에 없는 상품은 건너뛰고, 담기 이력은 남긴다.

- 다른 대안을 선택하지 않은 이유

| 대안 | 선택하지 않은 이유 |
| --- | --- |
| 결제 트랜잭션 안에서 삭제(처음 시도) | 1절의 롤백 범위·잠금 순서·도메인 경계 문제 |
| `@Async` 비동기 리스너 | 정리 작업이 가벼운 DELETE·UPDATE뿐이다. 비동기로 하면 결제 직후 장바구니에 상품이 남아 보일 수 있고, 서버 종료 시 큐의 작업이 사라질 수 있으며, 스레드 풀·커넥션 사용량·MDC 전파를 따로 관리해야 한다. |
| 주문 상품에 장바구니 항목 ID를 저장해 그 행만 정리 | CodeRabbit이 "결제 대기 중 항목을 지우고 같은 상품을 다시 담으면 새 행까지 차감한다"고 지적했다. 맞는 지적이지만 주문 상품 컬럼·ERD·주문 생성을 모두 바꿔야 한다. 주문 생성부터 결제가 늦게 반영될 때까지(결제 대기 5분, 만료 뒤 1분 간격 재확인 10분, 그 뒤 Fallback·결제 대사) 같은 상품을 지우고 다시 담은 경우뿐이고 결과도 결제 수량만큼 덜 남는 정도라, 회원·상품 기준을 유지하고 요구사항 문장을 그 기준으로 고쳤다(`ed37115`). |

- 변경 코드
  - `src/main/java/com/recipick/domain/order/event/OrderPaidEvent.java`
  - `src/main/java/com/recipick/domain/order/event/PaidCartCleanupListener.java`
  - `src/main/java/com/recipick/domain/order/service/CartService.java`의 `deductPaidItems`
  - `src/main/java/com/recipick/domain/order/repository/CartItemRepository.java`의 `deleteByMemberIdAndProductIdAndQuantityAtMost`·`decreaseQuantity`
- 기존 데이터·운영에 필요한 조치: 없음

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 결제 성공 시 정리 | `PaymentCartCleanupIntegrationTest`: 결제 승인 후 장바구니 확인 | 결제한 상품이 남음 | 주문한 상품만 빠지고 주문에 없는 상품·담기 이력은 남음 | `8d75583`, `767ed88` |
| 정리 실패가 결제에 주는 영향 | 같은 테스트: `deductPaidItems`를 `@MockitoSpyBean`으로 바꿔 `CannotAcquireLockException`을 던지게 함(예외를 흉내 냄, 실제 DB 잠금 대기 없음) | (처음 시도) 결제 확정까지 롤백. 코드 흐름 분석이며 처음 시도 코드로 실행한 결과는 아님 | 결제 확정은 커밋된 채 승인으로 끝나고 장바구니만 남음 | `8d75583` |
| 결제 확정 롤백 | 같은 테스트: 이벤트 발행 뒤 결제 확정 롤백 | 해당 없음 | 커밋이 없어 장바구니를 정리하지 않음 | `767ed88` |
| 결제 대기 중 더 담은 수량 | 같은 테스트: 결제 대기 중 같은 상품 수량을 늘리거나 줄임 | 항목째 삭제 | 늘렸으면 결제 수량만 빼고 남김, 줄였으면 항목 삭제 | `11bdaf9` |
| 결제 실패 시 유지 | 같은 테스트: PG가 결제 실패를 확인 | 해당 없음 | 주문만 닫고 장바구니는 그대로 | `94773de` |
| 결제 만료 시 유지 | 같은 테스트: 결제 대기 5분이 지나 만료 | 해당 없음 | 주문만 닫고 장바구니는 그대로 | `94773de` |
| 같은 승인 재전달 | 같은 테스트: 결제 완료 뒤 같은 상품을 다시 담고 같은 승인을 다시 전달 | 해당 없음 | `ALREADY_PAID`로 끝나 다시 담은 항목을 지우지 않음 | `767ed88` |
| 전체 회귀 | `PaymentCartCleanupIntegrationTest` 7건 | 해당 없음 | 7건 통과 | 2026-11-03 `dev`(`69e7ba3`) 실행 |

실행 명령: `./gradlew test --tests '*PaymentCartCleanupIntegrationTest'` (Docker 필요, Testcontainers MySQL)

## 5. 재발 방지와 남은 작업

- 추가한 테스트·알림·문서
  - 위 통합 테스트 7건, 요구사항의 결제 완료 장바구니 정리 문장(회원·상품 기준, 다시 담은 항목 처리 명시)
  - 정리 실패 로그: 둘 다 `orderId`, `memberId`가 함께 찍힌다.

| 로그 | 의미 | 대응 |
| --- | --- | --- |
| `결제 완료 주문의 장바구니 정리 실패 — 장바구니에 상품이 남음` (WARN) | DB 오류로 정리 실패. 결제는 정상이다. | 해당 회원 장바구니에 결제한 상품이 남아 있는지 확인한다. 자동 재처리는 없다. |
| `결제 완료 주문의 장바구니 정리 중 예상하지 못한 오류` (ERROR) | 코드 버그 가능성. 결제는 정상이다. | 스택 트레이스를 보고 원인을 고친다. |

- 남은 제한
  - 리스너가 결제를 처리한 스레드에서 실행되므로, 확정 API·웹훅 워커·만료 재확인·결제 대사(`PaymentReconciliationFacade`)와 0원 결제 주문 생성(`POST /api/orders`)의 응답이 장바구니 정리 시간만큼 늦어진다.
  - 정리에 실패하면 자동 재처리 없이 상품이 장바구니에 남는다. 위 로그로 확인한다.
  - 회원 취소 실패 후 PAID 복구(`restorePaidAfterMemberCancelFailure`)는 `markPaid`를 거치지 않아 장바구니를 다시 정리하지 않는다. 의도한 동작이다.
- 다른 팀원에게 공유할 적용 방법
  - 기능을 넣을 트랜잭션을 고를 때 "이 작업이 실패하면 앞의 작업도 취소돼야 하는가"를 먼저 묻는다. 아니라면 같은 트랜잭션에 넣지 않는다.
  - 커밋 뒤 후처리는 `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(propagation = NOT_SUPPORTED)` 조합을 쓴다. 이미 커밋된 트랜잭션에서는 쓰기가 반영되지 않으므로, 후처리 메서드가 자기 트랜잭션을 새로 열어야 한다.
  - 이벤트는 여러 결제 성공 경로가 공통으로 거치는 지점(`markPaid`)에서 발행해야 새 경로가 생겨도 빠지지 않는다.
