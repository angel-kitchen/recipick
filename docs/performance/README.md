# 성능 보고서 목차

부하 테스트 → 병목 개선 → 캐싱 → 요청 수 제한은 같은 조건에서 잰 Before → After 수치로 이어집니다.
새 보고서는 [성능 보고서 양식](../templates/performance-report.md)을 복사해 `docs/performance/<영역>/<주제>.md`에 작성하고,
이 목차에 한 줄을 추가합니다.

## product (상품 검색·조회, 담당: 태헌)

| 순서 | 보고서 | 다루는 내용 |
| --- | --- | --- |
| 1 | [성능 측정용 시드 데이터](product/seed-data.md) | 측정용 상품·회원 데이터의 규모, 분포, 재현 명령 |
| 2 | [부하 테스트 (Before)](product/load-test.md) | k6 시나리오, 100명 동시 접속 구간의 지연·처리량·커넥션 풀 관측 |
| 3 | [상품 검색 병목 개선 (인덱스)](product/bottleneck.md) | `EXPLAIN ANALYZE`로 찾은 원인과 인덱스 개선 Before/After |
| 4 | [조회 캐시 (Redis)](product/caching.md) | 인덱스로 줄일 수 없는 조회를 줄이는 캐시 도입과 결과 |
| 5 | [요청 수 제한 (429)](product/rate-limit.md) | 한 클라이언트가 자원을 독점하지 못하게 하는 제한 장치와 결과 |

읽는 순서는 위 순서를 권장합니다. 앞 보고서의 수치가 뒤 보고서의 Before 기준이 됩니다.

관련 문서: [모니터링](../monitoring.md)(측정 지표·관리 포트), [동시성](../concurrency.md), [문서 목차](../README.md).
