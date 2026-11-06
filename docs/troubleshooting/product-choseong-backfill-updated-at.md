# 초성 채우기가 모든 행의 수정 시각을 바꾸게 되어 있었다

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 초성 채우기와 수정 시각 · 태헌 · 2026-11-06 |
| 영향 범위 | 머지 전 자체 리뷰에서 발견해 운영 영향은 없다. 그대로 병합했다면 첫 기동에서 모든 상품·레시피의 `updated_at`이 기동 시각으로 바뀌고, 레시피는 관리자 응답(`RecipeAdminSummaryResponse`·`RecipeAdminDetailResponse`)에 수정 시각이 나가 모든 레시피가 같은 시각에 수정된 것처럼 보였다 |
| 관련 이슈·PR·커밋 | #511, PR #531, `eef1c768` |
| 처리 상태 | 해결 |

## 1. 증상

- 기대한 동작과 실제 동작: 이름에서 파생한 `name_choseong`을 기존 행에 채우는 작업은 상품·레시피 내용이 바뀐 것이 아니므로 `updated_at`을 바꾸지 않아야 한다. 처음 구현은 엔티티 메서드(`fillNameChoseong()`)로 값을 바꾸고 변경 감지로 저장해 수정 시각이 갱신되는 구조였다.
- 발생 환경·재현 조건: 앱 시작 때 도는 `ProductChoseongBackfiller`·`RecipeChoseongBackfiller`(`ApplicationRunner`)가 `name_choseong IS NULL`인 행을 500건씩 읽어 채우는 첫 구현.
- 발견 경로: PR #531을 CodeRabbit 방식으로 자체 리뷰하면서 코드를 읽어 찾았다. 수정 전 동작은 실행해서 재현하지 않았고 코드 근거로 판단했다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 변경 감지로 저장하면 감사 필드가 갱신된다 | `BaseTimeEntity`의 `@LastModifiedDate` 확인 | 엔티티가 바뀌어 UPDATE가 나가면 `AuditingEntityListener`가 `updatedAt`을 갱신한다. 맞다 |
| 수정 시각이 화면·로직에 쓰인다 | `updatedAt` 사용처 검색 | 상품·레시피 업무 로직은 쓰지 않는다. 레시피 관리자 응답 두 개가 값을 내보낸다(표시 영향) |

확인된 원인: 파생 컬럼을 채우는 일을 엔티티 수정으로 했기 때문에 감사 필드 갱신이 함께 일어난다. 조회수 증가(`increaseViewCount`)가 이미 같은 이유로 벌크 UPDATE를 쓰고 있었다.

## 3. 해결

- 바꾼 내용과 이유: `ProductRepository`·`RecipeRepository`에 `@Modifying` 벌크 UPDATE(`updateNameChoseong(id, 초성)`)를 두고 Backfiller가 이를 호출한다. 벌크 UPDATE는 Auditing을 거치지 않아 `updated_at`이 그대로다. 더 이상 쓰지 않는 `fillNameChoseong()`은 지웠다.
- 다른 대안을 선택하지 않은 이유: `BaseTimeEntity`에서 특정 경우만 갱신을 건너뛰게 하면 모든 도메인에 영향이 있다. 컨벤션은 벌크 UPDATE가 `updated_at`을 함께 갱신하라고 하지만 조회수 같은 내용 변경이 아닌 갱신은 예외로 두고 있어 같은 기준을 따랐다.
- 변경 코드·설정·문서: [`ProductChoseongBackfiller`](../../src/main/java/com/recipick/domain/product/service/ProductChoseongBackfiller.java), [`RecipeChoseongBackfiller`](../../src/main/java/com/recipick/domain/recipe/service/RecipeChoseongBackfiller.java), [선택 근거 3-8절](../adr/product-domain.md)
- 기존 데이터·운영에 필요한 조치: 없음. 채우기는 앱을 시작할 때마다 NULL인 행만 처리하고 다 채우면 조회가 0건이라 끝난다.

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 수정 시각 유지 | `ProductChoseongBackfillerTest`·`RecipeChoseongBackfillerTest`의 `keepsUpdatedAt`: 수정 시각을 2020-01-01로 만들고 채운 뒤 값 비교 | 코드 근거로 갱신된다고 판단(실행 미확인) | 2020-01-01 그대로 | [상품](../../src/test/java/com/recipick/domain/product/service/ProductChoseongBackfillerTest.java), [레시피](../../src/test/java/com/recipick/domain/recipe/service/RecipeChoseongBackfillerTest.java) |
| 비어 있는 행만 채움·반복 실행·이름 없는 행 | 같은 테스트 클래스 | 해당 없음 | 통과(각 3개) | 같은 파일 |
| 전체 회귀 | `./gradlew test` | 해당 없음 | 전체 통과(8분 59초) | 로컬 Testcontainers |
| 대량 데이터 | `recipick_perf`(상품 100,661건)에 앱 기동 | 해당 없음 | 6분 23초, NULL 0건 | [병목 개선 5-2절](../performance/product/bottleneck.md) |

## 5. 재발 방지와 남은 작업

- 추가한 테스트·알림·문서·규칙: 위 테스트 6개. 파생 값을 일괄로 채우거나 집계 값을 갱신하는 작업은 변경 감지 대신 `@Modifying` 쿼리로 하고, `updated_at`을 바꿀지 말지 주석으로 이유를 남긴다.
- 남은 제한과 담당 이슈: 행마다 UPDATE 한 문장이라 10만 건에 6분 23초가 걸린다. 운영 상품 수가 많으면 배치 크기 조정이나 JDBC 배치를 검토해야 하며 아직 이슈는 만들지 않았다.
- 다른 팀원에게 공유할 적용 방법: 엔티티 수정 없이 DB 값만 보정하는 작업이 생기면 같은 방식을 쓴다.
