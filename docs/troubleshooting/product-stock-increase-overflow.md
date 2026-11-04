# 재고 증가에서 정수 오버플로로 음수 재고가 생길 수 있었다

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 재고 증가 int 오버플로 · 태헌 · 2026-10-22 |
| 영향 범위 | 관리자 재고 증가(`Product.increaseStock`). 큰 수를 넣으면 재고가 음수가 되고 조정 이력이 저장될 수 있었다. 머지 전 리뷰에서 발견했다 |
| 관련 이슈·PR·커밋 | #95, PR #113, `b9d2c91` |
| 처리 상태 | 해결(상한 초과 거부). 음수 재고 경계 한 건은 리뷰가 지적했고 수정하지 않았다 |

## 1. 증상

- 기대한 동작과 실제 동작: 재고 증가량이 `int` 범위를 넘으면 거절되어야 하는데, `int` 덧셈이 넘쳐서 음수 재고가 됐다.
- 재현 조건: 현재 재고 50에 `quantity = Integer.MAX_VALUE`로 증가를 요청한다.
- 발견 경로: PR #113의 CodeRabbit 리뷰(Major). 리뷰는 "이 메서드는 음수 재고와 조정 이력을 저장한다"고 짚었다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| `int` 덧셈이 넘친다 | `increaseStock`의 `totalQuantity += quantity` 확인 | 합이 `Integer.MAX_VALUE`를 넘으면 음수가 된다. 증가량이 양수인지만 검사했다(`validatePositive`) |

확인된 원인: 증가량의 양수만 검사하고 합의 상한은 검사하지 않았다.

## 3. 해결

- 바꾼 내용과 이유: `Product.increaseStock`에서 더하기 전에 `quantity > Integer.MAX_VALUE - totalQuantity`이면 `CommonErrorCode.INVALID_INPUT`으로 거절한다(`b9d2c91`). 거절 시 재고는 바뀌지 않는다. 엔티티 안에서 막아 모든 재고 증가 경로가 보호된다.
- 변경 코드·설정·문서: [`Product`](../../src/main/java/com/recipick/domain/product/entity/Product.java), [`ProductTest`](../../src/test/java/com/recipick/domain/product/entity/ProductTest.java)
- 필요한 조치: 없음

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 상한 초과 거절 | `ProductTest.increaseStocks_overflow` | 음수 재고 | `INVALID_INPUT`, 재고 50 그대로 | `b9d2c91` |

## 5. 재발 방지와 남은 작업

- 추가한 테스트: 위 단위 테스트 1개.
- 남은 제한: 같은 PR에서 리뷰가 한 건 더 지적했다. 재고가 음수일 때 `Integer.MAX_VALUE - totalQuantity` 계산 자체가 넘쳐서(예: 재고 -1) `increaseStock(1)`도 거절될 수 있다는 내용이고, 계산을 `long`으로 하자는 제안이었다. 현재 코드(2026-11-04)에는 반영되지 않았다. 다만 `decreaseStock`은 재고보다 많이 줄이면 거절하므로 정상 경로로는 음수 재고가 생기지 않는다. 빌더로 음수 재고를 만들 수 있는 경우에만 해당하며, 실제로 발생하는 경로는 확인하지 못했다. 필요하면 후속 이슈로 `long` 계산으로 바꾼다.
- 공유할 적용 방법: 수량을 더하는 엔티티 메서드는 합의 범위를 더하기 전에 검사하고, 검사식 자체가 넘치지 않는지도 확인한다.
