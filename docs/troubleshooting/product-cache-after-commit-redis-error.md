# Redis가 꺼졌을 때 마트 생성이 500을 반환했다(`transactionAware`)

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 커밋 후 캐시 삭제의 Redis 오류 전파 · 태헌 · 2026-10-28 (실측 2026-11-10) |
| 영향 범위 | 마트 생성 API. Redis가 꺼지면 마트는 DB에 저장됐는데 응답은 500이었다. 머지 전 리뷰에서 발견했다 |
| 관련 이슈·PR·커밋 | #229, PR #237, `ce91c19`(추가), `e52a9a4`(제거) |
| 처리 상태 | 해결(옵션 제거). 옛 값이 남는 현상은 TTL 안에서 허용 |

## 1. 증상

- 원래 설계: Redis가 꺼져도 캐시 오류는 로그만 남기고 DB로 처리한다(`CacheErrorHandler`).
- 내가 한 일: 캐시 삭제가 DB 커밋보다 먼저 일어나면 옛 값이 다시 저장될 수 있어서, 커밋 뒤에 삭제하는 `transactionAware()`를 추가했다(`ce91c19`).
- 리뷰 지적: PR #237에서 CodeRabbit이 "커밋 뒤에 난 Redis 오류는 `CacheErrorHandler`를 거치지 않고 API 실패가 된다"고 지적했다.
- 처음에는 이 지적을 코드만 보고 받아들였고 Redis를 실제로 끄고 호출해 보지는 않았다. 튜터 피드백(11/10)을 받고 직접 재현했다.

## 2. 원인

가설을 세우고 로컬에서 하나씩 확인했다. 실험용 Redis를 따로 띄우고(포트 16379), `transactionAware`가 있는 JAR와 없는 JAR를 같은 DB에 연결해 비교했다.

| 가설 | 확인 방법 | 결과 |
| --- | --- | --- |
| `transactionAware()`를 켜면 Redis가 꺼졌을 때 변경 API가 모두 실패한다 | 카테고리 이름 변경 PATCH를 Redis 중지 상태에서 호출 | **아니다.** 200이 나왔다. 이 경로는 컨트롤러가 서비스를 바로 호출해서 커밋 뒤에 캐시를 지우고, 그 오류는 `CacheErrorHandler`가 잡는다 |
| 바깥에 `@Transactional`이 있는 경로에서만 실패한다 | `MartFacade.createMart`(Facade가 트랜잭션을 열고 그 안에서 `MartService`가 캐시 삭제)로 마트 생성 POST를 Redis 중지 상태에서 호출 | **맞다.** 500이 나왔는데 DB에는 마트가 저장돼 있었다 |

원인: 트랜잭션 안에서 캐시를 지우면 `transactionAware()`가 삭제를 커밋 직후로 미룬다. 로그에서 `TransactionSynchronizationUtils.triggerAfterCommit` 다음 `TransactionAwareCacheDecorator$3.afterCommit`에서 예외가 올라오는 것을 확인했다. 이 시점의 예외는 `CacheErrorHandler`를 거치지 않는다. Facade처럼 트랜잭션이 바깥에 있는 경로에서만 문제가 되어서, 코드를 읽는 것만으로는 영향 범위를 정확히 알기 어려웠다.

## 3. 해결

- 바꾼 내용: `transactionAware()` 한 줄을 지웠다(`e52a9a4`, `CacheConfig.java`).
- 선택 이유: `transactionAware()`가 막아 주는 것은 삭제와 커밋 사이에 들어온 조회가 옛 값을 다시 저장하는 경우이다. 이론상 가능하지만 상한이 TTL(카테고리 10분, 마트 5분)이다. 켜 두면 Redis가 꺼졌을 때 저장은 성공했는데 에러가 나가는 쪽이라 더 나쁘다고 판단했다.
- 선택하지 않은 대안:
  - `transactionAware()` 유지: 위 이유로 제외.
  - 커밋 후 삭제를 직접 구현하고 try-catch로 감싸기: 동작은 하겠지만 캐시마다 삭제 코드를 따로 관리해야 해서 지금 규모에서는 과하다고 보고 보류했다.
- 변경 코드·설정·문서: [`CacheConfig`](../../src/main/java/com/recipick/global/config/CacheConfig.java), [캐싱 보고서](../performance/product/caching.md) 7절, [상품 ADR](../adr/product-domain.md) 3-2

## 4. 검증 결과

2026-11-10, 로컬(MySQL `recipick_demo`, Redis 7.4 도커). 조회는 `GET /api/categories` 20회 평균이다. 변경 요청 시간은 첫 호출의 JVM 워밍업이 섞여 참고용이다.

| 시나리오 | 코드 | 결과 |
| --- | --- | --- |
| Redis 켬 | 현재 | 조회 평균 16ms, 변경 200 |
| Redis 중지 | 현재 | 조회 평균 1,555ms(전부 200), 변경 200(DB 반영됨) |
| Redis 멈춤(`docker pause`) | 현재 | 조회 평균 1,560ms(전부 200), 변경 200(DB 반영됨) |
| Redis 켬 | `transactionAware` 켬 | 조회 평균 20ms, 변경 200 |
| Redis 중지, 카테고리 변경 | `transactionAware` 켬 | 변경 200(재현 안 됨) |
| Redis 중지, **마트 생성** | `transactionAware` 켬 | **500**, DB에는 마트 저장됨 |
| Redis 중지, 마트 생성 | 현재 | **201**, 356ms |

옛 값이 남는 위험도 직접 재현했다. 조회로 캐시를 채우고, Redis를 멈춘 채 이름을 바꾼 뒤(200), Redis를 되살려 조회하면 바뀌기 전 이름이 나온다. 이때 DB에는 새 이름이 있다. 캐시를 직접 비워야 새 이름이 보였고 남은 TTL은 584초(약 10분)였다. 문서에 적은 "상한은 TTL"이 실제로 그렇다는 근거이다.

측정 중 실수도 한 번 있었다. 위 현상 때문에 "원래 이름"을 캐시에서 읽었고, 그 옛 값으로 원복해서 DB 이름이 잘못 바뀌었다. 바로 원래 값으로 고쳤고 테스트 데이터는 모두 지웠다.

## 5. 재발 방지와 남은 작업

- 정정한 것: 이전 문서의 "카테고리·마트 변경 API가 실패할 수 있다"는 범위가 넓었다. 실제로 재현된 것은 Facade 트랜잭션 안에서 캐시를 지우는 경로(마트 생성)뿐이다.
- 새로 알게 된 문제(미해결): Redis가 꺼지면 API는 살아 있지만 요청이 약 1.5초로 느려진다. 요청 하나가 Redis를 3번 부르기 때문이다(요청 수 제한 카운터, 캐시 조회, 캐시 저장). 호출마다 `application.yml`의 `timeout: 500ms`만큼 기다린다. 이 설정이 없었다면 기본값(약 60초) 동안 스레드가 묶였을 것이다. 줄이려면 연속 실패 시 일정 시간 Redis 호출을 건너뛰는 서킷 브레이커를 검토해야 한다. 아직 구현하지 않았다.
- 공유할 방법: Spring Cache에 커밋 후 처리 옵션을 켤 때는 그 캐시 호출이 트랜잭션 안에서 일어나는 경로가 있는지, 그 시점의 예외가 오류 처리기를 거치는지를 Redis를 실제로 끄고 확인한다.
