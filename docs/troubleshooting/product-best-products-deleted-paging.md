# 베스트 상품: 삭제된 상품 때문에 페이지가 덜 차고 전체 개수가 커졌다

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 베스트 상품 페이지·개수 불일치 · 태헌 · 2026-11-03 |
| 영향 범위 | `GET /api/products/best`의 페이지 내용과 `totalElements`·`totalPages`. 머지 전 리뷰에서 발견했다 |
| 관련 이슈·PR·커밋 | #409, PR #423, `eb7a5a9` |
| 처리 상태 | 해결 |

## 1. 증상

- 기대한 동작과 실제 동작: 삭제된 상품은 빼야 하고(요구사항), 페이지는 가득 차고 개수는 실제와 같아야 한다. 최초 구현은 삭제 상품을 **페이지를 자른 뒤에** 걸러서 페이지가 덜 찼고, 개수 쿼리는 삭제 상품까지 세서 `totalElements`·`totalPages`가 실제보다 컸다.
- 발견 경로: PR #423 팀 리뷰. 리뷰어가 예시로 "상위 20개 안에 삭제된 상품이 3개면 첫 페이지에 17개만 내려가고 개수도 커진다"고 짚었다.
- 재현: 실제 데이터로 재현하지는 않았고, 코드 흐름으로 확인했다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 순위 쿼리가 삭제 상품을 모른다 | 주문 도메인 쿼리(`findBestSellingProductIds`)와 상품 조회 코드 확인 | 주문 도메인은 판매량으로 ID만 정렬해 `Page<Long>`을 만들고, 삭제 여부는 상품 도메인이 페이지 뒤에 걸렀다 |
| 개수 쿼리가 삭제 상품을 센다 | `COUNT(DISTINCT oi.productId)` 확인 | 주문 쪽 쿼리는 상품 삭제 여부를 알 수 없어 그대로 센다 |

확인된 원인: 페이지 계산(자르기와 개수)이 삭제 상품을 거르기 전의 ID 목록 기준이었다. 도메인 간 규칙상 주문 도메인이 상품 테이블을 직접 조인할 수 없어서, 쿼리에서 삭제 상품을 빼는 방법(리뷰의 첫 제안)은 쓸 수 없었다.

## 3. 해결

- 바꾼 내용과 이유: 주문 도메인(`OrderSalesService`)은 상위 100개 상품 ID만 순서대로 돌려주고(`BEST_PRODUCT_LIMIT = 100`), `ProductFacade`가 조립하고, `ProductService.findBestProducts`가 삭제·다른 마트 상품을 먼저 거른 목록 기준으로 페이지를 자르고 개수를 센다. 개수 쿼리는 지웠다.
- 다른 대안을 선택하지 않은 이유: 주문 쪽 집계 쿼리에서 삭제 상품을 제외하는 방법은 주문 도메인이 상품을 직접 조인해야 해서 도메인 간 호출 규칙에 어긋난다.
- 감수한 제한: 베스트는 상위 100개 안에서만 페이지가 나뉜다. 베스트 화면 용도라 충분하다고 판단했다.
- 변경 코드·설정·문서: [`ProductService`](../../src/main/java/com/recipick/domain/product/service/ProductService.java), [`OrderSalesService`](../../src/main/java/com/recipick/domain/order/service/OrderSalesService.java), [`ProductFacade`](../../src/main/java/com/recipick/domain/product/facade/ProductFacade.java)
- 필요한 조치: 없음(조회 기능, 데이터 변경 없음)

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 삭제 상품 제외 후 페이지·개수 | `ProductServiceTest`("베스트: 삭제 상품을 거른 목록 기준으로 페이지를 잘라 빈 칸 없이 채운다" 외 2개), `ProductFacadeTest`(2개) | 페이지가 덜 차고 개수가 큼 | 삭제 상품을 거른 목록 기준으로 계산 | `eb7a5a9`의 테스트 수정 |
| 집계 순서·기간·상태 필터 | `OrderItemRepositoryTest` | - | 통과(3개) | [ADR](../adr/product-domain.md) 5절 |
| 순위 쿼리 속도 | 로컬 `EXPLAIN ANALYZE` | - | 24시간 17.6ms ~ 전체 936ms | [인덱스 미사용](product-ranking-index-not-used.md) |

## 5. 재발 방지와 남은 작업

- 추가한 테스트: `ProductServiceTest`의 베스트 테스트 3개(순위 순서 유지와 삭제·다른 마트 상품 건너뛰기, 삭제 상품을 거른 뒤 페이지 채우기, 빈 순위). [상품 ADR](../adr/product-domain.md) 3-5 참고.
- 남은 제한: 상위 100개 밖의 상품은 순위에 나오지 않는다. 순위 쿼리는 요청 때마다 집계한다(캐시하지 않음).
- 공유할 적용 방법: 다른 도메인의 정보(삭제 여부 등)로 걸러야 하는 순위·페이지는 먼저 조립 계층에서 거른 뒤에 자르고 센다. 개수 쿼리를 따로 두면 필터와 어긋나기 쉽다.
