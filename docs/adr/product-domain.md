# 상품 조회·성능·재고·순위 설계 결정과 검증 결과

상품 검색은 `EXPLAIN ANALYZE`로 찾은 느린 쿼리를 인덱스로 고치고, 인덱스로 줄일 수 없는 조회는 Redis 캐시로 줄이며, 한 IP의 폭주는 필터의 요청 수 제한으로 막습니다.
재고는 상품 행을 비관적 락(PK 오름차순)으로 잠근 뒤 검증·차감하고, 찜·조회수·판매량 순위는 기간 기준을 하나(`RankingPeriod`)로 모아 집계합니다.
각 결정의 측정 과정과 수치는 [성능 보고서 목차](../performance/README.md)의 보고서에 있고, 이 문서는 결정과 근거를 한곳에 모읍니다.

| 항목 | 내용 |
| --- | --- |
| 주제 | 상품 검색 인덱스·조회 캐시·요청 수 제한·재고 잠금·순위 집계·모니터링 공개 규칙, 이름 초성 검색·검색어 자동완성, 할인·판매가, 마트·카테고리·상품 등록 규칙 |
| 담당 | 태헌 (레시피 순위 D8·D9는 레시피 담당 보민 확인 필요) |
| 작성·최종 확인일 | 2026-11-06 (D12~D19 추가) |
| 상태 | 구현, 문서 리뷰 대기 |
| 관련 요구사항 | [요구사항](../requirement.md)의 상품 검색·상품 상세·카테고리·마트 조회, 관리자 재고 조정, 레시피 찜·조회수 순위, 베스트 상품(2차) |
| 관련 이슈·PR | 순위 기능 #409(PR #423), 문서 정리 #429(PR #430), 레시피 자동완성 #498, 할인·판매가 #494, 초성 검색·상품 자동완성 #511(PR #531), 목록 검색 띄어쓰기 #532(PR #533), 성능 보고서는 각 보고서에 기록 |
| 검증한 코드 기준 | D1~D11 `9b173d2`(2026-11-03, #428 병합 뒤 `dev`), D12~D19 `8c1faac4`(2026-11-06, #533 병합 뒤 `dev`) |
| 확인 환경 | Java 17, Spring Boot 3.5.16, Testcontainers MySQL·Redis, 로컬 `./gradlew test` |

## 사용 기술 한눈에 보기

| 영역 | 기술 | 하는 일 | 고른 이유 | 자세히 |
| --- | --- | --- | --- | --- |
| 조회 | 복합 인덱스 3개(`Product` `@Table(indexes)`) | 키워드·필터 검색의 전체 행 읽기와 정렬 제거 | 원인이 DB 연결 부족이 아니라 느린 쿼리여서 | D1, D2 |
| 캐시 | Spring Cache + Redis(`CacheConfig`) | 카테고리·영업 중 마트·상품 검색 결과 저장 | 이미 쓰는 Redis, 서버가 늘어도 캐시 공유 | D3, D4 |
| 보호 | `RateLimitFilter` + Redis Lua(`INCR`+`PEXPIRE`) | IP별 분당 요청 수 제한, 초과 시 `COMMON-012`(429) | 새 의존성 없이 로그인 잠금과 같은 카운터 구조 | D5 |
| 잠금 | 상품 행 `PESSIMISTIC_WRITE`(`findAllByIdInForUpdate`, `findByIdForUpdate`) | 재고 차감·복구·관리자 조정 | 같은 상품의 동시 변경은 한쪽씩 처리. 낙관적 락은 컨벤션상 금지 | D6, D7 |
| 집계 | `RankingPeriod` + JPQL `GROUP BY`/`COUNT` | 일간 24시간·주간 7일·월간 30일·전체 기준 순위 | 기간 규칙을 한 곳에 두고 찜·판매량이 같이 사용 | D8, D9 |
| 카운터 | 벌크 `UPDATE ... view_count + 1` | 레시피 조회수 증가 | 읽고 더해 저장하는 갱신 유실 방지, 락 시간 최소화 | D10 |
| 관측 | Actuator 관리 포트 8081 + Prometheus·Grafana | 지표 수집, 앱 포트로는 비공개 | 지표를 인터넷에 열지 않고 Before/After를 같은 대시보드로 확인 | D11 |
| 이름 검색 | `name_choseong` 컬럼 + `Choseong`(`global/common`) | 초성만 입력한 검색을 컬럼 비교로 처리, 상품·레시피 공통 | Elasticsearch 없이 규모·일정에 맞고 색인 동기화가 필요 없어서 | D12, D13 |
| 자동완성 | 상품 DB 조회 8건 + Redis 30초 캐시, 레시피 메모리 비교 | 검색창 제안(띄어쓰기·대소문자 무시, 초성 가능) | 상품은 마트당 수만 건이라 DB에서 8건만, 레시피는 100개라 메모리 | D14, D15 |
| 가격 | 판매가를 저장하지 않고 `Product.getSalePrice(now)`로 계산 | 할인율·종료 시각에 따른 판매가를 모든 화면·주문이 같은 기준으로 사용 | 종료 시각이 지나면 별도 작업 없이 정상가로 돌아가고 기준이 한 곳 | D16 |
| 마트·카테고리 | 행 단위 비관적 락 + 감사 기록 + 캐시 전체 무효화 | 마트·카테고리·상품 등록의 중복·권한 검증을 직렬화 | DB UNIQUE로 못 막는 조건(삭제 제외)을 락으로 보완 | D17~D19 |

## 1. 상황

회원은 이용 마트의 상품을 검색하고 베스트·인기 레시피를 보며, 마트 관리자는 상품·재고를 관리합니다. 상품 검색(`GET /api/products`)은 이름 포함 검색(`LIKE '%키워드%'`),
카테고리·가격 필터, 정렬을 조합하는 가장 무거운 조회입니다.

- 성능 제약
  - 측정용 시드 상품 10만 건(마트당 5만 건)에서 100명 동시 접속 시 상품 검색 p95 2.66s, DB 커넥션 풀(HikariCP 최대 10개) 대기 요청 최대 69개가 관측됐습니다([부하 테스트](../performance/product/load-test.md)).
  - 같은 시나리오에서 개선 전후를 비교해야 해서, 측정 데이터와 시나리오는 고정했습니다([시드 데이터](../performance/product/seed-data.md)).
- 일관성 제약
  - 같은 상품의 재고를 주문·결제 복구·관리자 조정이 동시에 바꿔도 재고가 음수가 되거나 변경이 유실되면 안 됩니다.
  - 순위 조회와 조회수 증가가 레시피 상세 응답을 막으면 안 됩니다.
- 운영 제약: 앱 서버 여러 대, Redis·MySQL 공유를 전제로 JVM 안 락·메모리 캐시는 쓰지 않습니다. 컨벤션상 낙관적 락·`NOWAIT`·`REQUIRES_NEW`는 쓰지 않습니다.
- 해결됐다고 판단할 기준
  - 같은 시나리오·조건에서 Before/After 수치(p95·p99·풀 대기)로 개선을 확인합니다.
  - 재고 경합은 동시성 테스트로 재현해 초과 판매 0건을 확인합니다.
  - 순위는 기간 경계·삭제·다른 마트 데이터가 결과에 섞이지 않는지 통합 테스트로 확인합니다.

## 2. 결정 목록

| # | 결정 | 근거 절 |
| --- | --- | --- |
| D1 | 상품 검색 인덱스 3개를 `EXPLAIN ANALYZE` 결과로 추가한다 | 3-1 |
| D2 | 커넥션 풀 크기·풀텍스트 인덱스 대신 느린 쿼리 개선을 먼저 한다 | 3-1 |
| D3 | 캐시는 Redis(카테고리 10분·영업 중 마트 5분·상품 검색 30초)로 하고, 상품 상세와 주문 금액 계산은 캐시를 쓰지 않는다 | 3-2 |
| D4 | Redis 장애 시 캐시 오류는 로그만 남기고 DB로 대체하며, 캐시 삭제는 `transactionAware()` 없이 커밋 전에 한다 | 3-2 |
| D5 | 요청 수 제한은 필터 + Redis 고정 윈도우 카운터로 직접 구현하고, Redis 오류 시 통과(fail-open)시킨다 | 3-3 |
| D6 | 재고 차감·복구는 상품 행을 PK 오름차순으로 한 번에 비관적 락으로 잠근 뒤 검증·변경한다 | 3-4 |
| D7 | 관리자 재고 조정은 상품 1건을 잠그고 조정 이력과 감사 기록을 같은 트랜잭션에 남긴다 | 3-4 |
| D8 | 순위 기간은 `RankingPeriod`(24시간·7일·30일·전체=2000-01-01 기준) 한 곳에서 정하고, 순위 API는 클라이언트 `sort`를 무시한다 | 3-5 |
| D9 | 베스트 상품은 결제 완료(`PAID`) 주문을 기준으로 마트별 판매량 상위 100개 상품을 뽑고 그 안에서 페이지를 자른다 | 3-5 |
| D10 | 레시피 조회수는 상세 조회와 다른 짧은 트랜잭션에서 벌크 UPDATE로 올리고, 실패해도 응답은 막지 않으며 `updated_at`은 바꾸지 않는다 | 3-6 |
| D11 | 상태·지표 엔드포인트는 관리 포트(8081)에서만 열고 `health`, `prometheus`만 노출한다 | 3-7 |
| D12 | 이름 초성 검색은 Elasticsearch 대신 상품·레시피의 `name_choseong` 컬럼을 비교한다. 초성만 입력했을 때만 이 컬럼을 쓰고 그 외에는 이름을 비교한다 | 3-8 |
| D13 | 기존 행의 초성은 앱 시작 때 NULL인 행만 500건씩 별도 트랜잭션의 벌크 UPDATE로 채우고, `updated_at`은 바꾸지 않는다 | 3-8 |
| D14 | 상품 검색어 자동완성은 DB에서 이용 마트의 상품 8건만 조회하고 30초 캐시한다. 50자 초과·빈 검색어는 조회도 캐시도 하지 않는다 | 3-9 |
| D15 | 목록 검색과 자동완성은 이름과 검색어의 띄어쓰기를 모두 지우고 비교한다 | 3-9 |
| D16 | 판매가는 저장하지 않고 정상가·할인율·할인 종료 시각으로 조회 때 계산한다. 일괄 할인은 상품 ID 오름차순 잠금 뒤 한 건이라도 안 되면 전체를 되돌린다 | 3-10 |
| D17 | 마트는 지역당 하나, 위치·반경은 슈퍼 관리자만 바꾸고 마트 관리자는 본인 마트의 운영 상태만 바꾼다. 영업 중 마트 목록은 5분 캐시하고 마트 변경 때 비운다 | 3-11 |
| D18 | 카테고리는 소프트 삭제하고, 상품이 연결돼 있으면 삭제를 거절하며, 삭제된 이름으로 등록하면 복구한다 | 3-12 |
| D19 | 같은 마트에서 삭제되지 않은 상품의 이름·브랜드 중복은 마트 행 잠금 뒤 검사로 막고, 상품 삭제는 소프트 삭제로 한다 | 3-12 |

## 3. 결정별 상황·후보·선택

### 3-1. 상품 검색 인덱스 (D1, D2)

- 상황: 부하 테스트에서 풀이 가득 차고 대기가 쌓였습니다. 풀이 가득 찬 원인이 쿼리 하나가 연결을 오래 쥐는 것일 수 있어 실행 계획부터 확인했습니다.
- 확인한 원인(`EXPLAIN ANALYZE`, 로컬 `recipick_perf`, 상품 10만 건): 키워드 검색은 마트 상품 5만 건을 전부 읽고 `filesort`했습니다. 목록 103ms + 개수 71ms로 한 요청이 연결을 약 175ms 쥐었고, 풀 10개로는 초당 약 57번이 한계라 대기 69개와 일치했습니다.
- 후보

| 후보 | 장점 | 비용·한계 |
| --- | --- | --- |
| **복합 인덱스(선택)** | 쿼리 코드를 바꾸지 않고 읽는 행과 정렬을 줄임 | 쓰기가 조금 느려지고 저장 공간이 늘어남(쓰기 부하 미측정) |
| 커넥션 풀 크기 증가 | 설정 한 줄 | 원인이 느린 쿼리면 DB 부담만 늘림 |
| 풀텍스트(ngram) 인덱스 | 개수 쿼리를 22ms까지 줄임 | JPA가 `MATCH ... AGAINST`를 지원하지 않아 쿼리 코드 변경이 큼 |

- 선택: `idx_product_mart_category_price`(필터·가격 내림차순), `idx_product_mart_category_price_asc`(가격 오름차순, `id DESC`), `idx_product_mart_created`(키워드·최신순)
- 선택한 이유: 같음 조건(`mart_id`, `category_id`, `deleted`)을 앞에, 범위·정렬(`price`)을 뒤에 두면 조건을 만족하는 행만 읽고 개수 쿼리는 인덱스만으로 끝납니다. 정렬 순서와 같은 최신순 인덱스는 키워드가 든 20건이 모이면 읽기를 멈춥니다. 앱이 가격 오름차순을 `price ASC, id DESC`로 만들어서 `id DESC` 인덱스를 따로 뒀습니다.
- 코드 근거: [`Product`](../../src/main/java/com/recipick/domain/product/entity/Product.java)의 `@Table(indexes)`, `ProductRepositoryImpl.searchMemberProducts`
- 측정 결과: 키워드 목록 103ms → 1.2ms, 필터 개수 7.2ms → 0.24ms(단독 실행). 부하 테스트 p95 2.66s → 264ms(3회 중앙값), 풀 대기 69 → 23. 자세한 표는 [병목 개선 보고서](../performance/product/bottleneck.md).

### 3-2. 조회 캐시 (D3, D4)

- 상황: 인덱스 이후에도 키워드 검색 개수 쿼리(`LIKE '%키워드%'`) 약 70~80ms는 인덱스로 줄일 수 없었고, 카테고리·영업 중 마트처럼 모든 사용자가 같은 값을 읽는 조회가 매번 DB를 거쳤습니다.
- 후보

| 후보 | 장점 | 비용·한계 |
| --- | --- | --- |
| **Redis(선택)** | 이미 쓰는 인프라, 서버가 여러 대여도 캐시와 무효화를 공유 | 조회마다 네트워크 왕복(약 1ms), `record` 응답은 타입 지정 JSON 직렬화가 필요 |
| Caffeine(앱 메모리) | 가장 빠르고 설정이 간단 | 서버마다 캐시가 따로라 무효화가 어긋남 |
| 캐시 없이 쿼리만 개선 | 구조 단순 | 개수 쿼리처럼 인덱스로 못 줄이는 비용이 남음 |

- 선택: `categories` 10분(생성·수정·삭제 시 전체 삭제), `openMarts` 5분(생성·수정 시 전체 삭제), `productSearch` 30초(TTL만 사용)
- 선택한 이유: 새 인프라가 필요 없고 서버가 늘어도 캐시가 일관됩니다. 왕복 1ms는 쿼리 70~100ms에 비해 작습니다.
- 제외한 것: 상품 상세는 재고가 주문마다 바뀌어 오래된 값의 위험이 커서 제외했습니다. 주문·장바구니는 캐시를 거치지 않는 `ProductService.findProducts`로 가격을 읽어 금액 계산에 영향이 없습니다.
- 실패 처리(D4): `CacheErrorHandler`가 조회·저장·삭제 오류를 로그로만 남기고 DB 조회로 넘어갑니다. `transactionAware()`로 커밋 뒤에 삭제하면 옛 값 재저장 경합은 막지만, 커밋 뒤 Redis 오류가 API 실패로 번져 Redis 장애 때 가용성이 깨져 채택하지 않았습니다. 읽기 캐시는 TTL 안의 오래된 값을 허용하고 가용성을 우선했습니다.
- 코드 근거: [`CacheConfig`](../../src/main/java/com/recipick/global/config/CacheConfig.java), `CategoryService`·`MartService`·`ProductService.searchProducts`의 `@Cacheable`·`@CacheEvict`
- 측정 결과: p95 264ms → 19.8ms, 풀 대기 23 → 0, 사용 중 연결 10 → 5, 상품 검색 적중률 약 97.8%(3회 중앙값). 한계는 4절에 정리했습니다. 자세한 표는 [캐싱 보고서](../performance/product/caching.md).

### 3-3. 요청 수 제한 (D5)

- 상황: 요청 수를 제한하는 장치가 없어 한 IP가 로그인·검색을 반복 호출하면 풀과 BCrypt 연산을 독점할 수 있었습니다. 로그인 실패 횟수 잠금은 있었지만 요청 자체를 막는 장치는 없었습니다.
- 후보

| 후보 | 장점 | 비용·한계 |
| --- | --- | --- |
| **필터 + Redis 카운터 직접 구현(선택)** | 새 의존성 없음, 로그인 잠금과 같은 구조, 서버가 늘어도 한도 유지 | 알고리즘·예외 처리를 직접 관리 |
| Bucket4j 등 라이브러리 | 토큰 버킷·분산 저장소 연동이 준비돼 있음 | 이번 규모(IP별 분당 N회)에는 의존성과 설정이 과함 |
| 앱 메모리 카운트 | 가장 빠름 | 서버마다 따로 세서 서버 수만큼 한도가 커짐 |
| 프록시·게이트웨이 제한 | 앱 코드 불필요 | 배포 담당 영역이고 로그인만 더 엄격하게 하는 정책을 나누기 어려움 |

- 선택: 고정 윈도우(기본 1분), `auth` 정책 분당 10회·`api` 정책 분당 120회(IP별), 웹훅·관리 포트 제외
- 선택한 이유: Redis 명령 두 개(`INCR`, 첫 요청에서만 `PEXPIRE`)로 끝나고, 둘을 Lua 스크립트로 묶어 장애 시 만료 없는 카운터가 남아 그 IP가 계속 막히는 일을 방지했습니다.
- 실패 처리: Redis 오류는 경고 로그만 남기고 통과(fail-open)시킵니다. 제한 장치 때문에 서비스가 멈추면 안 된다는 판단입니다. `spring.data.redis.timeout` 500ms를 명시해 fail-open이 바로 동작하게 했습니다.
- 코드 근거: `global/filter/RateLimitFilter`, `infra/redis/RedisRateLimitStore`, `RateLimitProperties`
- 측정 결과: 같은 IP 연속 호출 130회에서 200 × 120 + 429 × 10, 로그인 15회에서 400 × 10 + 429 × 5, 정책별로 따로 셈. 악성 IP 1개와 일반 회원 20명을 함께 돌린 k6에서 일반 회원 중앙값 응답이 757ms → 166ms, 풀 평균 대기가 27.4 → 2.0건으로 줄었습니다. 자세한 내용은 [요청 수 제한 보고서](../performance/product/rate-limit.md).

### 3-4. 재고 차감·복구 (D6, D7)

- 상황: 같은 상품 재고를 주문 생성(차감), 결제 실패·만료·환불(복구), 마트 관리자 조정이 동시에 바꿉니다. 읽고 더한 뒤 저장하면 변경이 유실되거나 재고가 음수가 됩니다.
- 후보

| 후보 | 장점 | 비용·한계 |
| --- | --- | --- |
| **비관적 락 `PESSIMISTIC_WRITE`(선택)** | 겹치는 요청을 한 줄로 세워 검증과 변경을 같은 잠금 안에서 처리, 컨벤션에 맞음 | 같은 상품에 몰리면 대기, 락 시간 초과는 `COMMON-010` |
| 낙관적 락(`@Version`) | 충돌이 드물면 대기 없음 | 컨벤션상 금지, 재고처럼 충돌이 잦은 자원에 재시도 비용이 큼 |
| 조건부 UPDATE(`qty - n >= 0`) | 원자적이고 가벼움 | 상품 판매 상태·마트 일치 검증과 가격 확정을 함께 하기 어려움 |

- 선택: 주문 흐름은 `findAllByIdInForUpdate`로 상품 ID를 `ORDER BY p.id`(오름차순)로 한 번에 잠근 뒤 삭제·마트 일치·판매 상태를 검증하고 차감합니다. 관리자 조정은 `findByIdForUpdate`로 1건을 잠급니다.
- 선택한 이유
  - 한 주문의 여러 상품을 PK 오름차순으로 잠가, 서로 다른 순서로 잠그는 두 주문이 교착하는 것을 막습니다.
  - 주문은 잠근 상태의 가격으로 금액을 계산해야 해서 차감한 상품 정보를 요청 순서대로 돌려줍니다.
  - 결제 잠금 순서(`PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고`)에서 재고는 맨 마지막입니다([동시성 전략](../concurrency.md)).
- 관리자 조정(D7): 감소량이 현재 재고를 넘으면 `STOCK_ADJUST_EXCEEDS_AVAILABLE`, MART_ADMIN은 토큰의 `martId`와 상품 마트가 같아야 합니다. 조정 이력(`StockAdjustment`)과 감사 기록은 같은 트랜잭션이라 감사 기록이 실패하면 조정도 롤백됩니다.
- 코드 근거: [`ProductStockService`](../../src/main/java/com/recipick/domain/product/service/ProductStockService.java)의 `decreaseStocks`·`increaseStocks`·`adjustStock`, [`ProductRepository`](../../src/main/java/com/recipick/domain/product/repository/ProductRepository.java)의 `findAllByIdInForUpdate`·`findByIdForUpdate`

### 3-5. 순위 집계: 기간 기준과 베스트 상품 (D8, D9)

- 상황: 레시피 찜 순위(기간별), 조회수 순위, 마트별 베스트 상품(기간별) 화면에 필요한 집계입니다. 기간 규칙이 API마다 달라지면 같은 "주간"이 다른 의미가 됩니다.
- 후보

| 후보 | 장점 | 비용·한계 |
| --- | --- | --- |
| **요청 시 DB 집계 + 기간 규칙 한 곳(선택)** | 새 테이블·배치 없음, 항상 최신 | 데이터가 커지면 집계 비용 증가, 인덱스에 의존 |
| 일 단위 집계 테이블(배치) | 조회가 빠름 | 배치·정합성·ERD 변경 필요, 이번 일정에 과함 |
| 판매량 컬럼을 상품에 누적 | 조회가 빠름 | 취소·환불·기간별 집계를 못 함 |

- 선택: `RankingPeriod`(DAILY 24시간, WEEKLY 7일, MONTHLY 30일, ALL은 `2000-01-01` 고정 시각)로 "이 시각 이후"를 구하고, 찜은 `GROUP BY recipe ORDER BY COUNT DESC`, 판매량은 `PAID` 주문의 `paid_at >= from`으로 집계합니다. 순위 API는 클라이언트 `sort`를 무시합니다.
- 베스트 상품(D9)
  - 판매 기준은 결제 완료입니다. 식재료 마트 서비스라 상품이 상한 경우가 아니면 단순 변심 환불이 없다는 전제로 정했습니다.
  - 주문 도메인이 마트별 판매량 상위 100개 상품 ID를 돌려주고(`OrderSalesService`), 상품 도메인이 삭제·다른 마트 상품을 걸러 페이지를 자릅니다. Service끼리 직접 부르지 않고 `ProductFacade`가 조립합니다.
  - 상위 100개로 자르는 이유는 `BEST_PRODUCT_LIMIT`로 집계 결과의 크기를 고정하기 위해서입니다(100위 밖 상품은 베스트에 나오지 않습니다).
- 인덱스: `idx_orders_mart_id_status_paid_at`(`mart_id, status, paid_at`), `idx_recipe_bookmark_created_at_recipe_id`(`created_at, recipe_id`)
- 코드 근거: [`RankingPeriod`](../../src/main/java/com/recipick/global/common/RankingPeriod.java), `OrderSalesService`, `ProductService.findBestProducts`, `ProductFacade.findBestProducts`, `RecipeService.findPopularRecipes`

### 3-6. 레시피 조회수 증가 (D10)

- 상황: 레시피 상세를 열 때마다 조회수가 올라야 합니다. 상세 조회 트랜잭션 안에서 올리면 관리자가 레시피 행을 잠그고 있을 때 회원 상세 조회가 대기하거나 실패합니다.
- 후보

| 후보 | 장점 | 비용·한계 |
| --- | --- | --- |
| **상세 조회 뒤 짧은 트랜잭션 + 벌크 UPDATE(선택)** | 행 락을 UPDATE 한 문장 동안만 잡고, 읽고 더하는 갱신 유실이 없음 | 조회수 증가 실패 시 그 조회는 세지 않음 |
| 상세 조회와 같은 트랜잭션 | 단순 | 회원 응답이 레시피 행 락에 묶임 |
| Redis 카운터 + 주기 반영 | 쓰기 부하가 작음 | 반영 배치·유실 처리 필요, 이번 범위에 과함 |

- 선택한 이유: 조회수는 정확히 한 번씩 세는 값이 아니어도 되지만 응답이 막히면 안 됩니다. 증가 실패는 `WARN` 로그만 남기고 응답은 그대로 보냅니다. 새 트랜잭션 전파(`REQUIRES_NEW`) 대신 Facade가 조회 트랜잭션이 끝난 뒤 Service를 따로 호출하는 구조입니다.
- `updated_at`을 바꾸지 않는 이유: 조회수는 레시피 내용 변경이 아니고, 카운터 증가마다 수정 시각이 바뀌면 "마지막 수정" 의미가 사라집니다. 컨벤션에 이 예외를 적었습니다.
- 한계: 같은 회원의 새로고침도 매번 셉니다(조회수 조작 방지는 하지 않았습니다).
- 코드 근거: [`RecipeFacade`](../../src/main/java/com/recipick/domain/recipe/facade/RecipeFacade.java), `RecipeService.increaseViewCount`, `RecipeRepository.increaseViewCount`

### 3-7. 모니터링 공개 규칙 (D11)

- 상황: 헬스 체크와 지표를 수집해야 하지만 인터넷에 열면 내부 상태가 노출됩니다. `SecurityConfig`는 `anyRequest().denyAll()`이라 `/actuator/**`는 기본적으로 막혀 있습니다.
- 후보: 같은 포트에서 `health`만 공개하고 지표는 인증하는 방식(Prometheus가 토큰을 가져야 해 복잡) vs **관리 포트 분리(선택)**
- 선택: 관리 포트 8081(`MANAGEMENT_PORT`)에서만 `health`, `prometheus`를 열고, 앱 포트로는 열리지 않습니다. 헬스 응답은 `show-details: never`입니다.
- 감수하는 비용: 관리 포트는 인증이 없어 네트워크로만 보호되므로 서버 보안 그룹에서 8081을 막아야 합니다. 자세한 내용은 [모니터링](../monitoring.md).

### 3-8. 이름 초성 검색 (D12, D13)

- 상황: 레시피 자동완성(#498)은 `ㄱㅊ` 같은 초성을 지원하지만 상품·레시피 목록 검색(`nameContains`)은 `이름 LIKE %검색어%`라 초성이 0건이었습니다. 화면 안내문은 "초성도 돼요"라고 말하고 있었습니다. 상품은 성능 측정용 데이터가 10만 건, 레시피는 100개입니다.
- 후보
  - Elasticsearch(한글 분석기·초성 분석기): 정확하지만 서버 한 대 이상과 상품·재고·삭제를 따라가는 색인 동기화가 필요하고, 11/9 승격·11/12 발표 일정에 비해 운영 부담이 큽니다.
  - 메모리 필터(레시피 자동완성 방식): 상품 목록은 가격 조건·정렬·페이지가 쿼리에 묶여 있어 메모리에서 거르면 페이지 수와 전체 개수가 틀어집니다.
  - MySQL 풀텍스트(ngram): JPA가 `MATCH ... AGAINST`를 지원하지 않는 점은 D2와 같고, 풀텍스트로도 초성 분리는 해결되지 않습니다.
  - **`name_choseong` 컬럼(선택)**
- 선택: `PRODUCT`·`RECIPE`에 `name_choseong varchar(50)`을 두고, 엔티티를 만들거나 이름을 바꿀 때 `Choseong.of(name)`으로 함께 갱신합니다. 한글 음절은 초성으로 바꾸고 그 밖의 글자는 그대로 둡니다. 검색어가 초성만이면(`Choseong.isChoseongOnly`) 이 컬럼을 비교하고 아니면 이름을 비교합니다. `Choseong`은 상품과 레시피가 함께 써서 `global/common`에 둡니다.
- 기존 데이터(D13): `ddl-auto: update`는 컬럼만 추가하고 기존 행은 NULL로 두며, `data*.sql`이 넣는 시드 행도 NULL입니다. 그래서 `ProductChoseongBackfiller`·`RecipeChoseongBackfiller`(`ApplicationRunner`)가 앱을 시작할 때마다 `name_choseong IS NULL`이고 이름이 있는 행을 500건씩 읽어 채웁니다. 한 배치가 한 트랜잭션이고, 다 채운 뒤에는 조회가 0건이라 반복 실행해도 결과가 같습니다. 이름이 없는 행은 건너뜁니다(이 조건이 없으면 같은 행을 계속 다시 읽습니다).
- `updated_at`을 바꾸지 않는 이유: 처음에는 엔티티 메서드로 값을 바꿔 변경 감지로 저장했는데, 그러면 `@LastModifiedDate`가 발동해 모든 행의 수정 시각이 첫 기동 시각으로 바뀝니다. 레시피는 관리자 응답(`RecipeAdminSummaryResponse`)에 `updatedAt`이 있어 모든 레시피가 같은 시각에 수정된 것처럼 보입니다. 초성은 이름에서 파생한 값이라 내용이 바뀐 것이 아니므로 `@Modifying` 벌크 UPDATE(`updateNameChoseong`)로 바꿨습니다. 조회수 증가(D10)와 같은 이유입니다. 자체 리뷰에서 찾아 PR #531 병합 전에 고쳤고, `ProductChoseongBackfillerTest`·`RecipeChoseongBackfillerTest`가 수정 시각 유지·멱등·이름 없는 행 건너뛰기를 확인합니다.
- 감수하는 비용
  - `LIKE %검색어%`는 앞에 `%`가 붙어 인덱스를 못 타는 점이 기존과 같습니다.
  - 초성과 글자를 섞은 검색어(`김ㅊ`)는 지원하지 않고 이름 그대로 비교합니다.
  - 오타 교정·유의어·점수 정렬은 못 합니다.
  - 컬럼 하나가 늘고 첫 기동에 채우기가 한 번 돕니다. `recipick_demo`(상품 660·레시피 100건)는 약 2초였지만, `recipick_perf`(상품 100,661건)는 6분 23초(500건씩 202번, 행마다 UPDATE 한 문장)가 걸렸습니다(2026-11-06 로컬). 채우기는 앱 기동 로그(`Started`)가 찍힌 뒤에 실행되고 한 번 다 채우면 다음 기동부터는 바로 끝납니다. 운영 DB의 상품 수가 많으면 첫 배포 때 이 시간을 감안해야 합니다.
  - 시드 SQL로 넣은 행은 다음 기동 전까지 초성 검색에 잡히지 않습니다.
- 다시 검토할 조건: 상품이 수십만 건 이상으로 늘거나 오타 교정·유의어·랭킹이 필요해지면 Elasticsearch를 다시 검토합니다.

### 3-9. 검색어 자동완성과 띄어쓰기 (D14, D15)

- 상황: 레시피 검색창에는 자동완성(#498)이 있고 활성 레시피 100개를 메모리에서 비교합니다. 상품 검색창에도 같은 경험이 필요하지만 상품은 마트당 최대 약 5만 건입니다.
- 선택(D14): `GET /api/products/suggestions?keyword=`는 이용 마트의 삭제되지 않은 상품을 DB에서 조건으로 걸러 8건만 가져옵니다. 이름이 검색어로 시작하면 먼저, 그다음 이름순·ID순이며 상품 ID·이름·이미지를 줍니다. 초성만 입력하면 `name_choseong`을 비교합니다. 검색어가 비었거나 50자를 넘으면 DB를 거치지 않고 빈 목록을 주며 캐시에도 저장하지 않습니다. 결과는 `productSuggestions` 캐시에 30초 둡니다(키는 `마트ID:검색어`). 키 입력마다 오는 요청이라 프론트는 200ms 뒤에 요청하고, 실패하면 목록만 비우고 검색은 그대로 둡니다.
- 선택(D15): 자동완성에서 `김 치`로 `김치 500g`이 보이는데 Enter를 누른 목록은 0건이던 불일치를 없애려고, 목록 검색(`nameContains`)도 이름과 검색어의 띄어쓰기를 모두 지우고 비교합니다(`replace(이름, ' ', '')`, 이슈 #532·PR #533). 검색어의 띄어쓰기를 먼저 지운 뒤 초성만인지 판단하므로 `ㅇ ㅎㅂ`도 초성 검색이 됩니다.
- 감수하는 비용(2026-11-06, 로컬 `recipick_perf` 상품 10만 건·마트당 약 5만 건, 단독 `EXPLAIN ANALYZE`, 부하 상태가 아님)
  - 자동완성은 `replace`·`lower`를 씌워 인덱스를 못 타고 마트 전체를 읽습니다. 검색어 `대파`·`ㄷㅍ`(일치 4,156건을 정렬해 8건)는 같은 쿼리를 3번 쟀을 때 약 104~128ms였고, 처음 한 번은 버퍼가 데워지기 전이라 약 200ms였습니다. 같은 검색어는 30초 캐시를 쓰지만 서로 다른 검색어가 계속 오면 매번 이 비용이 듭니다.
  - 목록 키워드 검색에 `replace`를 씌운 비용은 검색어에 따라 달랐습니다. 마트 전체(약 5만 행) 개수 쿼리에서 `파`는 약 92ms에서 약 116ms(+25%), `대파`는 약 91ms에서 약 94ms(+3%)였고, 초성 `ㄷㅍ`은 `name_choseong` 비교로 약 95ms(일치 4,156건은 `대파`와 같음)였습니다. 카테고리 필터와 함께일 때는 약 5.8ms에서 약 6.4ms였습니다. 앞에 `%`가 붙는 `LIKE`는 원래 인덱스를 못 타므로 읽는 행 수는 같고 행마다 함수 계산이 늘어난 비용입니다.
  - 성능 보고서의 키워드 검색 수치(p95 548ms 등)는 이 변경 전 코드로 잰 값이라, 같은 시나리오를 다시 재면 조금 느리게 나올 수 있습니다.
  - 자동완성 요청은 요청 수 제한(IP별 분당 120회)과 같은 한도를 씁니다. 쉬지 않고 입력하면 닿을 수 있고, 실패해도 검색은 동작합니다.
- 다시 검토할 조건: 상품 수가 늘어 자동완성이 느려지면 공백을 지우고 소문자로 바꾼 이름을 컬럼으로 저장해 앞부분 일치에 인덱스를 쓰는 방식을 검토합니다.

### 3-10. 할인과 판매가 (D16)

- 상황(#494): 상품에 할인율과 할인 종료 시각을 지정하고, 반값특가 배지·일일특가·특가 상품만 보기·여러 상품 일괄 할인을 제공합니다. 검색·상세·장바구니·주문이 같은 판매가를 써야 합니다.
- 후보
  - 판매가를 컬럼에 저장하고, 할인 종료 때 스케줄러가 정상가로 되돌리는 방식: 값이 단순하지만 종료 시각이 지난 뒤 스케줄러가 돌기까지 틀린 가격이 보이고, 스케줄러 장애·중복 실행을 따로 다뤄야 합니다.
  - **정상가·할인율·종료 시각만 저장하고 판매가는 조회 때 계산(선택)**
- 선택: `price`는 정상가이고 `discount_rate`(0이면 할인 없음, 지정은 1~99)와 `discount_end_at`을 둡니다. `Product.getSalePrice(now)`가 기준 한 곳입니다. 할인율이 0보다 크고 종료 시각 전이면 `정상가 × (100 - 할인율) / 100`(원 단위 내림), 아니면 정상가입니다. 응답과 장바구니·주문은 이 메서드를 쓰고 검색 쿼리는 같은 규칙을 QueryDSL `CASE` 식(`ProductRepositoryImpl.salePrice`)으로 씁니다. 시각은 주입받은 `Clock`이라 종료 시각이 지나면 별도 작업 없이 정상가로 돌아갑니다.
- 일괄 할인: 한 번에 최대 100개를 상품 ID 오름차순으로 비관적 락(`findAllByIdInForUpdate`)을 잡은 뒤 지정합니다. 없는 상품·삭제된 상품·다른 마트 상품이 하나라도 있으면 전체를 되돌립니다(`PRODUCT_NOT_FOUND`, `MART_ACCESS_DENIED`). 마트 관리자는 본인 마트 상품만 지정할 수 있고, 변경 전·후 할인율·종료 시각이 관리자 감사 기록에 남습니다.
- 감수하는 비용: 가격 조건·정렬이 `price` 컬럼이 아니라 `CASE` 식이라 `price` 인덱스의 범위·정렬을 못 씁니다. 단독 `EXPLAIN ANALYZE`(상품 10만 건)에서 필터 검색 목록이 5.5ms(컬럼을 직접 쓰면 0.46ms), 개수가 5.4ms였습니다. 11/05 재측정에서 필터 검색이 10월보다 느려진 것과 방향은 맞지만, 부하 상태에서 이 식의 기여는 확인하지 않았습니다([병목 개선 5-1절](../performance/product/bottleneck.md), 재현은 `scripts/perf/explain-product-search.sql`). 현재 데이터에는 `discount_rate > 0`인 행이 없어 결과는 같고 실행 계획만 달라집니다. 지금 규모와 일정에서는 코드를 바꾸지 않기로 했습니다.
- 다시 검토할 조건: 할인 상품이 늘어나거나 가격 검색이 느려지면 가격 조건을 "할인 중 / 아닌 쪽"으로 나눠 아닌 쪽이 인덱스를 타게 하거나, 판매가를 컬럼으로 저장하는 방식을 검토합니다.

### 3-11. 마트 (D17)

- 상황: 회원은 배송지의 지역으로 이용 마트가 정해지고, 상품 가격·재고·판매 상태가 마트 기준입니다. 마트의 위도·경도·서비스 반경은 서비스 가능 범위 판정에 쓰입니다.
- 선택
  - 지역당 마트 하나입니다(`existsByRegionId`, `MART_REGION_DUPLICATED`). 위도는 ±90, 경도는 ±180, 반경은 0보다 커야 하고(`INVALID_MART_AREA`), 위도·경도는 둘을 함께 보낼 때만 바꿉니다.
  - 슈퍼 관리자는 모든 항목을 바꿉니다. 마트 관리자는 토큰의 `martId`와 같은 본인 마트의 운영 상태(`status`)만 바꾸고, 위치·반경을 보내면 `SUPER_ADMIN_ONLY`, 다른 마트면 `MART_ACCESS_DENIED`입니다. 검사는 서버에서 합니다.
  - 마트 수정은 마트 행을 비관적 락(`findByIdForUpdate`)으로 잠근 뒤 바꾸고, 값이 실제로 바뀐 경우에만 감사 기록(`MART_UPDATE`, `MART_STATUS_CHANGE`)을 남깁니다.
  - 영업 중 마트 목록은 `openMarts` 캐시에 5분 둡니다(전체 `all`, 지역별 `region:ID`). 마트를 등록하거나 수정하면 전체를 비웁니다. 지도용 목록(`GET /api/config/marts`)은 위도·경도가 없는 마트를 빼고 ID 순으로 줍니다.
- 감수하는 비용: 마트를 API로 바꾸면 캐시가 바로 비워지고, DB를 직접 고친 경우에만 최대 5분 늦게 반영됩니다. 영업 중 마트 캐시의 효과는 부하 시나리오에 호출이 없어 측정하지 못했습니다(6절).

### 3-12. 카테고리와 상품 등록 규칙 (D18, D19)

- 카테고리(D18)
  - 삭제는 `deleted`만 바꾸는 소프트 삭제입니다. 삭제되지 않은 상품이 연결돼 있으면 `CATEGORY_HAS_PRODUCTS`로 거절합니다.
  - 카테고리 이름은 유일하고, 삭제된 카테고리와 같은 이름으로 등록하면 새 행을 만들지 않고 복구(`restore`)합니다. 삭제된 이름으로 바꾸는 수정은 `CATEGORY_NAME_DUPLICATED`입니다.
  - 등록은 이름 행을 `findByNameForUpdate`로, 수정·삭제는 카테고리 행을 `findByIdForUpdate`로 잠근 뒤 검사해 동시 요청이 같은 이름을 두 번 만들지 못하게 합니다.
  - 변경할 때마다 `categories` 캐시(10분)를 전체 삭제합니다.
- 상품(D19)
  - 같은 마트에서 삭제되지 않은 상품은 이름과 브랜드가 모두 같으면 등록·수정할 수 없습니다(`PRODUCT_NAME_DUPLICATED`, 브랜드가 없는 상품끼리는 이름이 같으면 중복). "삭제된 상품 제외" 조건은 DB UNIQUE로 걸지 않고, 등록과 이름·브랜드 수정 때 마트 행을 `findByIdForUpdate`로 잠근 뒤 검사해 같은 마트의 동시 요청을 한 줄로 세웁니다.
  - 상품 삭제는 `deleted = true`로 바꾸는 소프트 삭제입니다. 장바구니·주문·레시피 연결이 상품을 참조하기 때문입니다.
  - 등록 직후 상태는 `SELLING`, 재고는 0입니다. 가격·판매 상태·재고는 각자 전용 API로만 바꾸고, 소속 마트는 바꿀 수 없습니다.
  - 재료를 연결할 때 상품의 판매 단위가 재료 단위 계열과 같아야 합니다(`UNIT_MISMATCH`). 재료 하나에 여러 상품을 연결할 수 있습니다.
  - 마트 관리자는 본인 마트 상품만 등록·수정·삭제합니다. 등록·수정·삭제·가격·판매 상태·할인·재고 변경은 관리자 감사 기록에 남습니다.
- 감수하는 비용: 중복 방지가 DB 제약이 아니라 락 뒤 검사라서, 마트 행을 건너뛰고 직접 INSERT하면 막지 못합니다. 상품 등록·수정이 마트 행 락을 잡으므로 같은 마트의 관리자 작업이 겹치면 순서대로 기다립니다.

## 4. 트레이드오프

- 얻는 점
  - 같은 시나리오에서 상품 검색 p95를 2.66s에서 19.8ms로 줄였고 풀 대기를 69에서 0으로 줄였습니다(로컬 측정).
  - 재고는 락 하나로 검증·변경을 묶어 초과 판매를 막고, 주문과 같은 순서 규칙(PK 오름차순)으로 교착을 피합니다.
  - 순위는 새 테이블·배치 없이 기존 데이터로 집계하고 기간 규칙이 한 곳에 있습니다.
  - Redis 장애 시 캐시와 요청 수 제한은 서비스를 멈추지 않고 우회합니다.
  - 초성 검색을 컬럼 하나로 풀어 Elasticsearch 없이 상품·레시피 목록 검색과 자동완성에 같은 기준을 씁니다.
  - 판매가를 저장하지 않고 계산해서 할인 종료 때 별도 작업이 필요 없고, 모든 화면·주문이 한 메서드를 씁니다.
- 감수하는 비용·해결하지 못한 부분
  - 상품 검색 응답의 가격·품절 표시가 최대 30초 늦게 반영될 수 있습니다(주문 금액은 캐시를 거치지 않음).
  - 캐시 적중률(약 97.8%)은 k6 시나리오의 검색어가 5개뿐이라 실제보다 높게 나온 값입니다. 실제 사용자 검색에서는 개선 폭이 더 작을 수 있습니다.
  - 카테고리 없이 가격 조건만 쓰는 검색은 인덱스를 만들지 않았고 단독 실행 약 88ms입니다.
  - Redis 장애 중에는 요청 수 제한이 풀립니다(fail-open). IP 기준이라 공유 IP 사용자는 한 묶음으로 셉니다.
  - 순위는 요청 시 집계라 데이터가 크게 늘면 느려질 수 있습니다. 베스트 상품·찜 순위는 캐시하지 않았습니다. 주문 10만 건에서 30일·전체 구간은 인덱스를 거의 쓰지 못해 0.5~0.9초가 걸렸습니다.
  - 인덱스가 늘어 상품 쓰기가 조금 느려질 수 있으나 측정하지 않았습니다.
  - 모든 측정은 로컬 단일 PC 결과입니다. 절대값이 아니라 같은 조건의 Before/After 비교에 의미가 있습니다.
  - 판매가 `CASE` 식과 이름 `replace` 비교는 인덱스를 못 타서 데이터가 커지면 느려집니다(각각 3-10, 3-9절). 현재 규모에서는 고치지 않았습니다.
  - 상품·마트 등록의 중복 방지는 DB 제약이 아니라 행 락 뒤 검사입니다(3-12절).
- 다시 검토할 조건
  - 순위 화면이 느려지거나 순위 쿼리가 슬로우 쿼리에 잡히면 순위 캐시나 집계 테이블을 검토합니다.
  - 실제 트래픽의 캐시 적중률이 낮으면 상품 검색 캐시 TTL·대상을 다시 정합니다.
  - 서버(AWS)가 확정되면 같은 시나리오로 다시 측정하고 요청 수 제한의 프록시 IP 전달을 확인합니다.
  - 조회수 조작이 문제가 되면 회원·IP별 중복 제거를 검토합니다.

## 5. 결과

테스트 결과는 로컬 `./gradlew test`(Testcontainers MySQL·Redis) 실행값입니다. 부하 수치는 로컬 `recipick_perf`, 상품 10만 건, k6 `scripts/perf/k6/product-search.js` 100명 동시, 3회 중앙값입니다.

| 검증 항목 | 방법·환경 | 기대 결과 | 실제 결과 | 근거 |
| --- | --- | --- | --- | --- |
| 상품 검색 인덱스 효과 | `EXPLAIN ANALYZE` 단독 실행 | `filesort`·전체 행 읽기 제거 | 키워드 목록 103ms → 1.2ms, 필터 목록 8.8ms → 1.1ms, 필터 개수 7.2ms → 0.24ms | [병목 개선](../performance/product/bottleneck.md) 4절 |
| 인덱스 부하 Before/After | k6 100명, 3회 중앙값 | p95·풀 대기 감소 | p95 2.66s → 264ms, p99 3.71s → 363ms, 풀 대기 69 → 23, 실패율 0% | [병목 개선](../performance/product/bottleneck.md) 5절 |
| 캐시 부하 Before/After | 같은 시나리오, 회마다 캐시 비움 | p95·풀 대기 추가 감소 | p95 264ms → 19.8ms, p99 363ms → 83.2ms, 풀 대기 23 → 0, 실패율 0% | [캐싱](../performance/product/caching.md) 5절 |
| 캐시 적중·무효화·오류 | `CacheIntegrationTest`, `CacheErrorHandlerTest` | 적중, 변경 시 삭제, 조건별 분리, Redis 오류 무시 | 통과(`CacheIntegrationTest` 5개, `CacheErrorHandlerTest` 1개) | [테스트](../../src/test/java/com/recipick/global/config/CacheIntegrationTest.java) |
| 요청 수 제한 동작 | `RateLimitFilterTest`(5개), 로컬 `curl` | 한도 초과 429, 정책별 분리, 웹훅 제외, Redis 오류 시 통과 | 통과(5개), `curl` 130회 중 200 × 120·429 × 10 | [테스트](../../src/test/java/com/recipick/global/filter/RateLimitFilterTest.java), [요청 수 제한](../performance/product/rate-limit.md) 5절 |
| 재고 동시 차감 | `ProductStockConcurrencyTest` | 재고 5개에 10건이 1개씩 차감하면 5건만 성공, 재고 0 | 통과(1개) | [테스트](../../src/test/java/com/recipick/domain/product/service/ProductStockConcurrencyTest.java) |
| 재고 차감·복구·조정 규칙 | `ProductStockServiceTest`, `ProductStockAdjustServiceTest`, `ProductStockHistoryServiceTest`, `ProductStockAdminControllerTest` | 삭제·다른 마트·판매 중지 거절, 초과 감소 거절, 이력 기록 | 통과(11 + 7 + 5 + 6개) | `domain/product` 테스트 |
| 순위 기간·집계 | `RankingPeriodTest`, `RecipeRankingRepositoryTest`, `OrderItemRepositoryTest` | 24시간·7일·30일·전체 시작 시각, 기간 안 찜·결제 완료 수량 합계 순서, 동수면 ID가 큰 쪽 먼저, 비활성 레시피·대상 아닌 주문 제외, 순위용 인덱스 컬럼 순서 | 통과(`RankingPeriodTest` 2개, `RecipeRankingRepositoryTest` 6개, `OrderItemRepositoryTest` 3개) | [기간](../../src/test/java/com/recipick/global/common/RankingPeriodTest.java), [찜](../../src/test/java/com/recipick/domain/recipe/repository/RecipeRankingRepositoryTest.java), [판매](../../src/test/java/com/recipick/domain/order/repository/OrderItemRepositoryTest.java) |
| 조회수 실패 격리 | `RecipeFacadeTest` | 증가가 실패해도 상세 응답 반환 | 통과(3개) | [테스트](../../src/test/java/com/recipick/domain/recipe/facade/RecipeFacadeTest.java) |
| 순위 인덱스 효과 | `EXPLAIN ANALYZE` 전후(주문 10만 건, 찜 9,440건) | 읽는 행 감소 | 베스트 상품 24시간 455ms → 17.6ms, 7일 274 → 208ms, 30일 608 → 552ms, 전체 1,185 → 936ms. 찜 순위는 17.8 → 18.8ms로 차이 없음 | 이 표 아래 "순위 인덱스 측정 상세" |
| 요청 수 제한 부하 Before/After | k6 악성 IP 1개 + 일반 회원 20명, 3회 중앙값 | 한 IP 폭주 시 풀 점유 감소 | 일반 회원 p95 1,343 → 791ms, 중앙값 757 → 166ms, 풀 평균 대기 27.4 → 2.0건, 일반 회원 429 0건 | [요청 수 제한](../performance/product/rate-limit.md) 5절 |
| 도메인 전체 테스트 | `./gradlew test --tests 'com.recipick.domain.product.*'`, `domain.recipe.*`, 캐시·요청 수 제한·`RankingPeriodTest` | 전체 통과 | 상품 20개 클래스 186개, 레시피 29개 클래스 179개를 포함한 53개 클래스 378개 통과, 실패·건너뜀 0 (`OrderItemRepositoryTest` 3개 통과는 별도 실행) | 로컬 실행 2026-11-03(`9b173d2`) |
| 초성·띄어쓰기 검색 | `ProductRepositoryTest`(초성 4개·띄어쓰기 2개), `RecipeSearchRepositoryTest`(5개), `ChoseongTest` | `ㅇㅎㅂ` 초성 검색, `ㅇ ㅎㅂ`·`대 파뿌` 띄어쓰기 무시, 글자 검색 유지, 삭제 상품 제외 | 통과 | [상품](../../src/test/java/com/recipick/domain/product/repository/ProductRepositoryTest.java), [레시피](../../src/test/java/com/recipick/domain/recipe/repository/RecipeSearchRepositoryTest.java) |
| 초성 채우기 | `ProductChoseongBackfillerTest`, `RecipeChoseongBackfillerTest` | 비어 있는 행만 채움, 수정 시각 유지, 반복 실행 결과 동일, 이름 없는 행 건너뜀 | 통과(각 3개) | [상품](../../src/test/java/com/recipick/domain/product/service/ProductChoseongBackfillerTest.java), [레시피](../../src/test/java/com/recipick/domain/recipe/service/RecipeChoseongBackfillerTest.java) |
| 상품 자동완성 | `ProductRepositoryTest`(5개), `ProductServiceTest`(2개), `CacheIntegrationTest` | 시작 일치 우선, 띄어쓰기·대소문자 무시, 초성, 삭제·다른 마트 제외, 8건 제한, 같은 검색어 캐시, 50자 초과 미캐시 | 통과 | [테스트](../../src/test/java/com/recipick/global/config/CacheIntegrationTest.java) |
| 11/05 재측정(최신 dev) | k6 100명, 같은 시나리오 3단계 | 단계별 p95·풀 대기 감소 | p95 1.87s → 492ms → 45.7ms, 풀 대기 62 → 23 → 0, 실패율 0%. 10월 값과 직접 비교하지 않음(After가 더 느려졌고 원인은 확정 못 함) | [병목 개선 5-1절](../performance/product/bottleneck.md), [캐싱 5-1절](../performance/product/caching.md), [성능 요약](../performance/README.md) |
| 자동완성·띄어쓰기·초성 쿼리 비용 | `EXPLAIN ANALYZE` 단독(2026-11-06, 상품 10만 건, 마트당 5만 건) | 읽는 행·시간 확인 | 자동완성 약 104~128ms(처음 한 번 약 200ms), 목록 키워드 검색 `replace` +3~25%, 초성 검색 약 95ms. 첫 기동 backfill 상품 100,661건 6분 23초 | 3-8·3-9절 |
| 상품·레시피 도메인 전체 테스트 | `./gradlew test --tests 'com.recipick.domain.product.*'`, `domain.recipe.*`, `global.common.*`, `CacheIntegrationTest`(2026-11-06, `8c1faac4`) | 전체 통과 | 상품 21개 클래스 218개, 레시피 36개 클래스 238개, `Choseong` 등 공통 2개 클래스 5개, 캐시 통합 7개, 실패 0 | 로컬 Testcontainers MySQL·Redis |

### 순위 인덱스 측정 상세

로컬 `recipick_perf`에 결제 완료 주문 90,000건(취소 10,010건 포함 100,010건), 주문 상품 300,047건, 찜 9,440건을 넣고 `EXPLAIN ANALYZE`를 돌렸습니다. Before는 새 인덱스를 `IGNORE INDEX`로 막은 상태, After는 힌트 없이 옵티마이저가 고른 상태입니다. 기간별 1회 측정입니다.

| 베스트 상품 기간 | Before | After | After에서 새 인덱스를 썼나 |
| --- | --- | --- | --- |
| 24시간 | 455ms | 17.6ms | 사용 |
| 7일 | 274ms | 208ms | 사용(강제해도 119ms) |
| 30일 | 608ms | 552ms | 쓰지 않음 |
| 전체 | 1,185ms | 936ms | 쓰지 않음(강제하면 1,624ms로 더 느림) |

- 인덱스 `(mart_id, status, paid_at)`는 기간이 짧을수록 읽는 행이 줄어 효과가 큽니다. 24시간은 약 26배 빨라졌습니다.
- 30일·전체는 주문의 절반 가까이를 읽어야 해서 효과가 작고, 전체는 옵티마이저가 쓰지 않는 쪽이 더 빠릅니다.
- 같은 쿼리를 힌트 없이 3회 재면 30일 구간은 Before 657~995ms, After 543~680ms로 겹칩니다. 24시간 구간만 차이가 뚜렷합니다.
- 찜 순위는 레시피가 100개뿐이라 Before 17.8ms, After 18.8ms(3회 중앙값)로 차이가 없습니다. 이 규모에서는 `(created_at, recipe_id)` 인덱스 효과를 입증하지 못했습니다.

## 6. 확인이 필요한 차이

| 항목 | 요구사항·기대 | 현재 코드·상태 | 확인할 사람 |
| --- | --- | --- | --- |
| 레시피 순위 D8·D9·D10 | 레시피 도메인 책임 | 구현은 태헌이 했고 레시피 담당 확인 전 | 보민 |
| 순위 인덱스 근거 | 베스트 상품은 짧은 기간(24시간·7일)에서만 효과, 찜 순위는 레시피 수천 건 이상에서 효과 | 24시간만 뚜렷하게 빨라졌고 30일·전체는 효과가 작음. 찜 순위는 레시피 100개 시드라 효과 측정 불가 | 태헌 |
| 요청 수 제한 | 프록시가 `X-Forwarded-For`를 덮어쓰는지 | 확인 못 함(덮어쓰지 않으면 헤더 위조로 우회 가능) | 배포 담당 |
| 요청 수 제한 429 | 프론트 다른 도메인에서도 429 본문 확인 | 필터가 Spring Security 앞이라 CORS 헤더가 없음 | 프론트·태헌 |
| Redis 장애 대체 | 실제 장애 시 API 동작 | 오류 처리기 단위 테스트만 확인, Redis를 끄고 호출하는 확인은 안 함 | 태헌 |
| 영업 중 마트 캐시 | 효과 측정 | 구현만 하고 부하 시나리오에 호출이 없어 측정 못 함 | 태헌 |
| 가격만 쓰는 검색 | 카테고리 없는 가격 필터 | 인덱스 없음, 단독 실행 약 88ms | 태헌, 필요 시 인덱스 추가 |
| 판매가 식과 가격 인덱스 | 가격 조건·정렬이 `price` 인덱스의 범위·정렬을 씀 | `CASE` 식이라 못 씀. 단독 `EXPLAIN` 5.5ms 대 0.46ms, 부하 상태 기여는 미확인 | 태헌, 할인 상품이 늘면 가격 조건 분리 검토 |
| 자동완성·띄어쓰기 비용 | 상품 수가 늘어도 입력 중 응답이 빠름 | 마트 5만 행 전체 읽기 약 104~128ms(처음 한 번 약 200ms), 30초 캐시와 디바운스로 완화. 부하 시나리오에는 포함 안 됨 | 태헌, 필요 시 정규화 컬럼 |
| 첫 기동 초성 채우기 시간 | 배포 때 기동이 길어지지 않음 | 상품 10만 건은 6분 23초(행마다 UPDATE). 운영 상품 수는 확인 전 | 태헌, 배포 담당 |
| 초성과 글자 혼합 검색 | `김ㅊ` 같은 입력 | 지원하지 않고 이름 그대로 비교 | 이슈 설계에서 범위 제외 |
| 서버(AWS) 환경 | 운영 환경에서도 같은 개선 | 로컬 단일 PC 측정뿐이라 서버에서 다시 측정해야 함 | 태헌, 배포 담당 |

## 7. 참고 자료

- [요구사항](../requirement.md), [컨벤션](../convention.md)의 트랜잭션·동시성·도메인 간 호출 절, [ERD](../erd.dbml)의 `product`·`orders`·`recipe_bookmark`·`stock_adjustment`
- [성능 보고서 목차](../performance/README.md): [시드 데이터](../performance/product/seed-data.md), [부하 테스트](../performance/product/load-test.md), [병목 개선](../performance/product/bottleneck.md), [캐싱](../performance/product/caching.md), [요청 수 제한](../performance/product/rate-limit.md)
- [동시성 전략](../concurrency.md), [모니터링](../monitoring.md), 상품 검색 쿼리 재현 스크립트 `scripts/perf/explain-product-search.sql`
- [MySQL EXPLAIN ANALYZE](https://dev.mysql.com/doc/refman/8.4/en/explain.html#explain-analyze), [MySQL 복합 인덱스](https://dev.mysql.com/doc/refman/8.4/en/multiple-column-indexes.html), [MySQL InnoDB 잠금](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html)
- [Spring Cache 추상화](https://docs.spring.io/spring-framework/reference/integration/cache.html), [Redis `INCR`](https://redis.io/docs/latest/commands/incr/), [Redis Lua 스크립트](https://redis.io/docs/latest/develop/interact/programmability/eval-intro/)
