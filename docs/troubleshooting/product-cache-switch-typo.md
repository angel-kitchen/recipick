# 캐시를 끄는 스위치가 동작하지 않았다(속성명 오타)

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 캐시 스위치 속성명 오타 · 태헌 · 2026-10-28 |
| 영향 범위 | `recipick.cache.enabled=false`로 캐시를 끌 수 없었다. 운영 장애는 없고 머지 전 리뷰에서 발견했다 |
| 관련 이슈·PR·커밋 | #229, PR #237, `ce91c19` |
| 처리 상태 | 해결 |

## 1. 증상

- 기대한 동작과 실제 동작: `recipick.cache.enabled=false`를 주면 `CacheConfig`가 등록되지 않아야 하는데, 값과 관계없이 항상 등록됐다.
- 발생 환경·재현 조건: Redis 캐시를 처음 도입한 PR #237. 테스트 설정(`src/test/resources/application-test.yml`)은 `recipick.cache.enabled: false`로 캐시를 끄는 것이 전제였다.
- 발견 경로: PR #237의 CodeRabbit 리뷰(Major). 실행 중 증상이 아니라 코드와 설정을 비교해서 찾았다.
- 관찰 결과: PR 본문에 "기존 통합 테스트는 `recipick.cache.enabled=false`라 캐시 영향이 없다"고 적었는데, 코드상 이 설명이 성립하지 않았다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 스위치 속성 이름이 설정과 다르다 | `CacheConfig`의 `@ConditionalOnProperty` 이름과 `application-test.yml` 키 비교 | 코드는 `recipick.cache.enaled`, 설정은 `recipick.cache.enabled`로 달랐다 |

확인된 원인: `@ConditionalOnProperty(name = "recipick.cache.enaled", havingValue = "true", matchIfMissing = true)`에서 `enabled`를 `enaled`로 썼다. 코드가 보는 속성이 어디에도 설정돼 있지 않아 `matchIfMissing = true` 때문에 항상 켜졌다. 오타가 나도 컴파일 오류나 시작 오류가 나지 않는 구조라 눈으로 비교하기 전에는 드러나지 않았다.

## 3. 해결

- 바꾼 내용과 이유: 속성 이름을 `recipick.cache.enabled`로 고쳤다(`global/config/CacheConfig.java`). 같은 커밋에서 상품 검색 캐시 키 표현식도 리뷰 지적에 따라 고쳤다. 그 지적의 상세 내용은 확인하지 못했다.
- 다른 대안을 선택하지 않은 이유: 해당 없음(단순 오타).
- 변경 코드·설정·문서: [`CacheConfig`](../../src/main/java/com/recipick/global/config/CacheConfig.java), [캐싱 보고서](../performance/product/caching.md)
- 필요한 조치: 없음

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 속성 이름 일치 | 코드와 `application-test.yml` 비교 | 불일치 | 일치 | `ce91c19` |
| 같은 유형의 오타 | `@ConditionalOnProperty` 전체 검색(2026-11-04) | - | 요청 수 제한 스위치는 설정 키와 일치, 나머지는 `ai.client` | 로컬 검색 |
| 스위치 동작 | 실행으로 직접 재현하지는 않았다 | - | - | 코드 비교와 리뷰 지적으로 판단 |

## 5. 재발 방지와 남은 작업

- 추가한 테스트·문서: 없다. 오타는 코드 비교로만 확인했다.
- 남은 제한: 스위치 이름과 설정 키가 어긋나도 컴파일·시작 오류가 없다. 요청 수 제한은 `RateLimitProperties`처럼 설정 클래스로 묶는 방법이 있지만 적용하지 않았다.
- 공유할 적용 방법: `@ConditionalOnProperty`를 새로 쓸 때는 설정 파일의 키를 복사해서 쓰고, 값을 `false`로 줬을 때 빈이 빠지는지 한 번 실행해 본다.
