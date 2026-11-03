# AI 조건 추천이 문장과 무관한 레시피를 추천하던 문제

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | AI 조건 추천의 무관한 추천 · 보민 · 2026-10-30 |
| 영향 범위 | AI 조건 추천(`POST /api/recipes/ai-recommendations`)의 응답 상태·추천 목록, `ai_request_log`의 결과 상태·실패 사유. 주문·장바구니·결제 데이터 영향 없음 |
| 관련 이슈·PR·커밋 | #295, PR #297(`5e7a253`, `3a1f2de`, `670f848`, `7a7d5c0`) |
| 처리 상태 | 해결. 프롬프트 v2의 실제 효과 측정은 API 키 수령 후 평가로 확인(후속 작업) |

## 1. 증상

- **기대한 동작:** "통오징어찜"처럼 지원하는 조건(요리 종류·음식 유형·조리 시간·맵기·제외 재료)이 하나도 없는 문장이나, 후보 중 맞는 레시피가 없는 문장에는 "맞는 레시피가 없다"(`NO_MATCH`)고 답한다.
- **실제 동작:** 문장과 상관없이 같은 찌개 5개(레시피 ID 1~5)가 계속 추천됐다.
- **발생 환경:** 로컬, `dev` 병합 전 AI 조건 추천 기능. 가짜 클라이언트(`FakeAiClient`)에서 항상 재현된다. 가짜 클라이언트는 "한식·양식"과 "N분"만 읽고, 후보 앞에서부터 5개를 고른다.
- **재현 순서:** 멤버십 회원으로 AI 조건 추천에 "통오징어찜"을 보낸다 → 응답 `RECOMMENDED`, 추천 5개가 ID 순 첫 레시피들이다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 추천 AI(③ 고르기)가 엉뚱한 레시피를 골랐다 | `AiRecipeRecommendFacade.recommend`의 흐름과 후보 조회 코드를 따라감 | 아님. AI가 고르기 전에 이미 후보가 문장과 무관했다 |
| 분석한 조건이 비면 후보 조회에 필터가 걸리지 않는다 | `RecipeService.findRecommendCandidates` 확인 | **맞음.** 조건이 모두 비어 있으면 `ACTIVE` 레시피 전체가 ID 오름차순으로 후보가 된다 |
| AI가 "맞는 게 없다"고 답해도 장애로 처리된다 | Facade의 고르기 이후 분기 확인 | **맞음.** 빈 목록이면 `AiRecipeRecommendResolver.select`가 빈 결과를 주고, 이를 응답 오류(`INVALID_RESPONSE`)로 보고 `FALLBACK`으로 후보 앞 5개를 줬다 |

확인된 원인은 두 경로가 같은 결과(ID 1~5)로 모이는 것이었다.

```text
조건 분석 결과가 비어 있음
→ 필터 없이 ACTIVE 레시피 전체가 후보(ID 오름차순)
→ ③ 고르기
   ├ 가짜 클라이언트: 후보 앞 5개 선택 → RECOMMENDED(ID 1~5)
   └ 실제 AI: 무관한 후보뿐이라 빈 목록을 주면 → 응답 오류로 처리 → FALLBACK(후보 앞 5개 = ID 1~5)
```

조건이 있는 문장이라도 AI가 "후보 중 맞는 게 없다"(빈 목록)고 판단하면 같은 `FALLBACK` 경로를 탔다.
추천 프롬프트도 "최대 5개 고른다"만 있어 맞는 레시피가 없을 때 빈 목록을 줘도 된다는 기준이 없었다.

## 3. 해결

| 커밋 | 바꾼 내용 | 이유 |
| --- | --- | --- |
| `5e7a253` | 요구사항의 `NO_MATCH` 범위를 "분석한 조건 없음 / 조건을 만족하는 레시피 없음 / AI가 맞는 레시피 없다고 판단"으로 확장 | 동작을 바꾸기 전에 기준 문서를 먼저 고침 |
| `3a1f2de` | `RecipeRecommendCondition.isEmpty()` 추가. 조건이 비면 후보를 조회하지 않고 `NO_MATCH`, 두 번째 AI 호출 없음 | 조건을 하나도 못 읽었는데 전체에서 고르면 문장과 무관한 추천이 나감. 호출 1회와 토큰도 아낌 |
| `670f848` | Claude 추천 응답 블록이 없으면 `INVALID_RESPONSE` 실패 | 아래에서 빈 목록을 정상 판단으로 쓰므로, 응답이 깨져서 빈 경우와 구분해야 함 |
| `7a7d5c0` | AI 성공 + 빈 목록이면 `NO_MATCH`, 이력은 성공·실패 사유 없음. 추천 프롬프트에 "맞는 레시피가 없으면 억지로 고르지 말고 빈 목록" 추가(v2) | 빈 목록은 장애가 아니라 AI의 정상 판단 |

- `isEmpty()`는 검증에서 버려진 값(모르는 enum, 범위 밖 맵기)도 빈 조건으로 본다. AI가 형식만 채우고 쓸 수 없는 값을 준 경우도 같은 기준을 탄다.
- 후보 밖 ID만 준 경우는 그대로 응답 오류(`FALLBACK`)로 둔다. 빈 목록(판단)과 엉뚱한 ID(오류)는 다르다.
- **선택하지 않은 대안**
  - 조건이 없으면 인기순 등 기본 추천을 준다: 사용자가 말한 것과 무관한 결과라 "추천"이라고 보여 주기 어렵다.
  - 빈 목록도 계속 `FALLBACK`으로 둔다: 장애 지표(실패율)에 정상 판단이 섞이고, 사용자는 맞지 않는 후보 5개를 받는다.
- **기존 데이터·운영 조치:** 없음. 이전 이력은 당시 상태 그대로 둔다.
- 변경 위치: [AiRecipeRecommendFacade](../../src/main/java/com/recipick/domain/recipe/facade/AiRecipeRecommendFacade.java), [RecipeRecommendCondition](../../src/main/java/com/recipick/domain/recipe/service/RecipeRecommendCondition.java), [ClaudeAiClient](../../src/main/java/com/recipick/infra/ai/claude/ClaudeAiClient.java), [프롬프트 이력 v2](../ai/prompt-history.md)

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 조건 없는 문장 | `AiRecipeRecommendFacadeIntegrationTest` "분석한 조건이 하나도 없으면 …" | `RECOMMENDED`, ID 순 5개 | `NO_MATCH`, 두 번째 AI 호출 없음, 이력 성공 | `3a1f2de` |
| AI가 빈 목록 | 같은 클래스 "AI가 후보 중 맞는 레시피가 없다고 빈 목록을 주면 …" | `FALLBACK`, 실패 사유 `INVALID_RESPONSE` | `NO_MATCH`, 이력 성공·실패 사유 없음 | `7a7d5c0` |
| 응답 블록 없음 | `ClaudeAiClientTest` "추천 응답 블록이 없으면 …" | 빈 추천으로 성공 | `INVALID_RESPONSE` 실패 | `670f848` |
| 후보 밖 ID만 응답(회귀) | 같은 Facade 테스트 "AI가 후보 안의 레시피를 하나도 고르지 못하면 …" | `FALLBACK` | `FALLBACK` 유지 | 기존 테스트 통과 |
| 전체 회귀 | `./gradlew test` | - | PR #297 시점 2,508건 실패 0·건너뜀 2. 2026-11-03 `dev` `8215c63`에서 3,384건 실패 0·건너뜀 2 | PR #297, [ADR 6절](../adr/recipe-ai.md) |

```bash
./gradlew test \
  --tests 'com.recipick.domain.recipe.facade.AiRecipeRecommendFacadeIntegrationTest' \
  --tests 'com.recipick.infra.ai.claude.ClaudeAiClientTest'
```

## 5. 재발 방지와 남은 작업

- **추가한 테스트:** 위 3건(조건 없음, 빈 목록, 응답 블록 없음). 이후 "빈 목록 = 판단, 깨진 응답 = 실패" 구분을 바꾸면 테스트가 깨진다.
- **문서:** 요구사항의 `NO_MATCH` 기준, [프롬프트 이력](../ai/prompt-history.md) v2, [ADR](../adr/recipe-ai.md) D6.
- **남은 제한**
  - "아무거나 추천해줘"처럼 원래 조건이 없는 문장도 `NO_MATCH`가 된다(의도한 동작). 화면은 직접 검색으로 안내한다.
  - "오징어 들어간"처럼 지원하지 않는 포함 재료 조건은 해석하지 못해 `NO_MATCH`가 된다. 조건 목록 변경이라 별도 이슈로 남겼다.
  - 프롬프트 v2로 "맞는 레시피가 있는데 빈 목록을 주는" 경우가 늘었는지는 API 키 수령 후 [평가](../ai/evaluation.md)로 확인한다. 현재 평가는 조건 분석까지만 다뤄 ③ 고르기는 범위 밖이다.
  - 자연어 장보기의 메뉴 분석은 응답 블록이 없어도 빈 목록으로 성공 처리해 기준이 다르다. 맞출지는 [ADR](../adr/recipe-ai.md) 8절 ⑦에서 결정한다.
- **팀에 공유할 점:** AI 결과가 "비어 있음"일 때는 정상 판단인지 응답 오류인지를 먼저 나누고, 정상 판단이면 장애 상태(`FALLBACK`)로 처리하지 않는다. 필터 조건이 모두 비면 "전체"가 아니라 "없음"으로 볼지도 기능마다 정한다.
