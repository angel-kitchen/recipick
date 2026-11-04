# 문서 목차와 작성 안내

루트 [README](../README.md)는 서비스 소개·실행·결과 요약을, 이 문서는 상세 근거와 작성 위치를 안내합니다.
상태는 `작성 대기 → 작성 중 → 검증·리뷰 → 반영 완료`로 갱신합니다. 구현 상태와 문서 작성 상태는 별도로 기록합니다.

## 기존 기준 문서

| 문서 | 확인할 내용 | 관리 |
| --- | --- | --- |
| [요구사항](requirement.md) | 1·2·3차 기능·정책과 도메인별 책임 | 담당자별, 공통 변경은 팀 합의 |
| [컨벤션](convention.md) | 아키텍처·트랜잭션·보안·Git·검증 규칙 | 팀 공통 |
| [컨벤션 요약](convention-summary.md) | 구현 시 빠르게 확인할 공통 규칙 | 상세 컨벤션과 함께 갱신 |
| [ERD DBML](erd.dbml) | 테이블·컬럼·관계·제약 | 도메인 담당, 공통 확인 |
| [모니터링](monitoring.md) | 관리 포트·상태 확인·지표 공개 규칙 | 태헌 |
| [성능 시드](performance/product/seed-data.md) | 데이터 분포·재현 명령·사용 범위 | 태헌 |
| [성능 보고서 목차](performance/README.md) | 부하·병목·캐싱·호출 제한 보고서와 읽는 순서 | 태헌 |
| [정적 분석](static-analysis.md) | SonarQube·JaCoCo 실행 방법·결과 위치·이슈 처리 근거 | 기준 변경은 팀 합의 |

요구사항 원본을 바꾸면 저장소 루트에서 `python3 .coderabbit/split_requirements.py`를 실행하고
`.coderabbit/requirements/` 결과도 함께 검토·커밋합니다. 문서 틀만 바꿀 때는 재생성이 필요하지 않습니다.

## 공통 작성 양식

| 양식 | 사용할 곳 | 필수 내용 |
| --- | --- | --- |
| [기술 선택 근거](templates/technical-decision.md) | `docs/adr/<주제>.md` | 상황 → 후보 → 선택과 이유 → 트레이드오프 → 결과 |
| [성능 보고서](templates/performance-report.md) | `docs/performance/<영역>/<주제>.md` ([목차](performance/README.md)) | 환경·데이터·재현 명령·Before/After·정합성 |
| [트러블슈팅](templates/troubleshooting.md) | `docs/troubleshooting/<주제>.md` | 증상 → 원인 → 해결 → 재발 방지 |
| [AI 활용 기록](templates/ai-work-record.md) | 팀 노션 TASK 본문에 복사 | 근거·방식·과정·사람이 검토·수정한 내용·검증 |

템플릿은 원본을 유지하고 복사해 사용합니다. 실제 문서를 추가하면 아래 표의 예정 경로를 클릭 가능한 상대 경로 링크로 바꿉니다.
아직 없는 파일은 링크 대신 코드 경로로 표시했습니다.

## 기술 선택·구현 근거 작성 목록

| 주제 | 작성 위치 | 담당 | 문서 상태·완료에 필요한 근거 |
| --- | --- | --- | --- |
| 계층형 아키텍처 | [선택 근거](adr/layered-architecture.md) | 채빈 | 검증·리뷰: 전체 도메인 책임·계층·협력 구조와 실제 사례 확인 |
| 인증·인가 | [선택 근거·검증](adr/authentication.md) | 채빈 | 검증·리뷰: 인증·비밀번호 변경·탈퇴 12개 클래스·177건 및 지급·체험·구독 API 3개 클래스·57건의 기존 실행 근거. C-17·체험/구독 진입점·멤버십 정산 Checker 반영 |
| 동시성 | [전략·검증 결과](concurrency.md) | 각 도메인, 회원 부분 채빈, 레시피 부분 보민, 결제 부분 정윤 | 회원 부분 검증·리뷰: 인증·기존 경합·추가 멤버십 32개 클래스·366건의 기존 실행 근거. 체험 API 동시 요청·구독 멱등·정산 잠금·충돌 계약 반영. 회원·상품·레시피·주문·결제 근거 연결. 실제 전체 정산·운영 성공 검증과 과거 실행 기록은 구분 |
| 결제·웹훅·환불 정합성 | [선택 근거](adr/payment-consistency.md) · [동시성 결제 절](concurrency.md#6-결제-도메인-승인만료취소웹훅포인트) · [결제 API 명세](payment-api.md) · [회원·멤버십 ↔ 결제 연동](member-payment-integration.md) · [결제 추적성 검토](payment-observability.md) | 정윤, 주문 연동 준희 | 검증·리뷰: 웹훅 재조회·지연 큐와 Fallback·PG 호출과 트랜잭션 분리·잠금/UNIQUE/멱등 키 역할·늦은 승인과 SYSTEM 환불·자동청구 선점. 결제 패키지 136개 클래스·1,673건 통과(2026-11-04 `9172f6d`, 작성자 작업 환경의 로컬 MySQL 8.0 실행, CI는 Testcontainers MySQL 8.4. 근거·재현 명령은 [동시성 6-5절](concurrency.md#6-5-실제-동시-요청실패-결과)). 추적성 개선은 도입 검토 단계 |
| 자동 구성·AI | [선택 근거·검증](adr/recipe-ai.md) | 보민, AI 공동 | 작성 중: 서버 계산·AI 역할·선택 근거·AI 필수 항목 점검 작성. 실제 Claude 평가(모델 3개)·모델 확정(Sonnet)·프롬프트 v4 및 냉장고 파먹기 Tool Calling v5 평가 기록 반영. SDK Tool로 구현됐으며 과제 문구의 `@Tool` 해석 확인은 ADR에 남김 |
| 상품 조회·성능·재고·순위 | [선택 근거·검증](adr/product-domain.md) | 태헌, 레시피 순위 보민 확인 | 작성: 검색 인덱스·캐시·요청 수 제한·재고 잠금·순위 집계·모니터링 공개 규칙. 순위 인덱스 EXPLAIN과 요청 수 제한 부하 수치는 측정 예정 |
| 부하·병목·캐싱·호출 제한 | [부하 테스트](performance/product/load-test.md) · [로컬 100명 결과](performance/local-100-users.md) · [병목 개선](performance/product/bottleneck.md) · [캐싱](performance/product/caching.md) | 태헌, 각 도메인 협조 | 로컬 100명 혼합 검증·API별 지연·정합성 결과 추가. 기존 병목·캐싱 보고서는 유지하며 운영 측정·제출 수치는 담당자 확인 |
| 배포·운영·모니터링 | [배포 선택 근거](deploy-strategy.md) · [구현 기록](deploy-implementation.md) · [운영 절차](../deploy/README.md) · [모니터링](monitoring.md) | 배포 준희, 모니터링 태헌, 보안 채빈 | 기존 문서로 연결. HTTPS·프론트 쿠키·운영 브라우저 검증 결과는 담당자와 갱신 |
| 트러블슈팅 | [로컬 k6 타임아웃](troubleshooting/k6-docker-host-timeout.md) · 레시피·AI: [AI 조건 추천 무관한 추천](troubleshooting/recipe-ai-recommend-no-match.md) · 주문: [잠금 전 엔티티 조회로 변경 덮어씀](troubleshooting/order-stale-entity-before-lock.md) · [회원 잠금 테스트 경합 없이 통과](troubleshooting/order-member-lock-test-false-pass.md) · [결제 완료 장바구니 정리](troubleshooting/order-paid-cart-cleanup.md) · 상품: [재고 증가 오버플로](troubleshooting/product-stock-increase-overflow.md) · [캐시 스위치 오타](troubleshooting/product-cache-switch-typo.md) · [커밋 후 캐시 삭제의 Redis 오류](troubleshooting/product-cache-after-commit-redis-error.md) · [베스트 상품 삭제 상품 페이지](troubleshooting/product-best-products-deleted-paging.md) · [조회수 증가 락 대기](troubleshooting/product-view-count-lock-wait.md) · [Prometheus 수집 23초](troubleshooting/product-prometheus-scrape-slow.md) · [요청 수 제한 테스트 IP 구분](troubleshooting/product-rate-limit-test-client-ip.md) · [순위 인덱스 미사용](troubleshooting/product-ranking-index-not-used.md) | 문제를 해결한 담당자 | 로컬 k6 실행 경로 분리·임시 대응(Docker 내부 원인은 미확정으로 구분), 레시피·AI 1건, 주문 3건, 상품 8건 작성 |

위 표는 현재 문서 위치와 기록된 검증 범위를 안내합니다. 담당·측정 범위가 달라지면 갱신합니다.
개별 기술 선택 문서는 해당 양식을 쓰고, 여러 도메인의 동시성 결과는 공통 문서의 도메인별 절로 모읍니다.

2026-11-03 추가한 [로컬 검증 결과](performance/local-100-users.md#4-사전-기능-검증과-남은-항목)는 앱 `bbd0943` 자동 테스트 3,384건 및 당시 로컬 화면 점검을 구분합니다. 아래 회원 기능 표의 구현 상태는 2026-11-04 `544bee2`에서 확인했으며, 과거 수치를 최신 dev 전체 E2E 완료로 읽지 않습니다. 멤버십 화면의 실제 PG 연결·탈퇴 성공 등 남은 검증은 추가 보고서를 따릅니다.

## 회원 기능 문서 반영 상태

구현 상태는 2026-11-04 `dev` `544bee2` 기준으로 대조했습니다. 기존 #382의 `434ea0f` 전체 실행 3,091건·실패 0·오류 0·실제 AI 평가 건너뜀 2건과 이후 `bbd0943` 로컬 검증 기록은 각각 당시 실행의 근거입니다. 이번 문서 갱신에서 최신 dev 전체 테스트를 재실행한 것은 아니며, 코드 연결·기본 OFF 설정·운영 성공 검증을 구분합니다.

| 항목 | 현재 상태·다음 반영 |
| --- | --- |
| 비밀번호 변경 | 인증 근거·API 참고 계약 반영, 전체 리프레시 무효화·기존 액세스 유지·경합·롤백 검증 기록 |
| 회원 탈퇴·체험 차단 | API·상태 차단·개인정보 정리·체험 차단 기반 및 PRODUCT_PAYMENT·POINTS·MEMBERSHIP_PAYMENT 실제 Checker 연결. AUTO_BILLING·카드 정리 준비 계약을 [회원 탈퇴 API](api/member-withdrawal.md)에 연결. AUTO_BILLING 실제 Checker도 구현됐으며 카드 정리 스위치 2개가 모두 true일 때 영역을 선언. 기본 OFF 설정에서는 MEMBER-008로 차단. 장바구니 삭제·담기 이력 유지는 연결됐고 찜은 탈퇴 제한 조건이 아니며 삭제는 이번 범위에서 제외 |
| 관리자 감사 기록 | 기록·조회·만료 정리와 기존 상품·레시피·주문 API 호출 연결. 결제의 MartRejectionProcessor에도 ORDER_REJECT 호출이 있으며 멤버십 결제 목록·상세·재조회 감사 호출도 #380에서 연결. 멤버십 환불 재조회·재실행 감사 기록도 연결 |
| 3차 멤버십 | 해지 예약·관리자 이력 API, 활성화·유예 판정, 결제 반영·소유 회원 매핑 계약, [체험 시작 API](api/membership-trial-start.md)와 활성 카드·C-17 검사, 관리자 지급 C-17 연결 반영. [유료 구독 API](api/membership-subscribe.md) 연결 반영. [즉시 해지 API](api/membership-terminate.md) 연결 반영. 탈퇴 준비·최종 처리 연결 완료. 실제 원격 카드 정리와 정상 탈퇴 성공 검증은 별도 |
| API 명세·SA·AI 활용 기록 | 팀 노션에서 갱신. 저장소 문서 수정만으로 노션 반영 완료 처리하지 않음 |

## 구현된 API의 저장소 참고 계약

- [주문 API 명세](order-api.md): 장바구니·주문 조회/생성·관리자 수락·거절·배송 변경. [주문 연동](order-payment-integration.md)과 함께 확인합니다.
- [즉시 해지](api/membership-terminate.md) · [회원 탈퇴](api/member-withdrawal.md): 환불·카드 정리의 비동기 상태와 활성화 경계.
- [무료체험 시작](api/membership-trial-start.md): 활성 카드·C-17 확인과 7일 체험 생성.
- [유료 구독 시작](api/membership-subscribe.md): 동의·멱등 키·결제 상태 응답과 C-11 호출 경계.
- [멤버십 결제 정합화](api/membership-reconciliation.md): 결제 담당의 관리자 조회·재조회·웹훅·복구·정산 Checker 계약.
- [결제 API 명세](payment-api.md): 결제·취소·웹훅·빌링키·포인트·관리자 결제 API(기존 21개와 마트 거절 환불 재처리 추가)의 요청·응답·오류 코드·멱등 규칙.
- [회원·멤버십 ↔ 결제 연동 기준](member-payment-integration.md): 구독·체험 전환·갱신·즉시 해지 환불·탈퇴 정산의 호출 책임과 트랜잭션 경계.
- [회원 비밀번호 변경](api/member-password-change.md): 요청·오류·전체 리프레시 세션 무효화·DB 컬럼 반영. 팀 노션 API 명세와 함께 확인합니다.

## 추가 설계·문제 해결 기록

- 주문: [설계 결정·검증](adr/cart-order.md) · [추적성 도입 검토](order-observability.md).
- AI·데이터: [초기 Spring AI 설계 검토](ai-design-review.md)(과거 제안) · [유튜브 레시피 적재 계획](recipe-youtube-import.md)(데이터 작업 계획). 현재 AI 구현·평가는 [AI ADR](adr/recipe-ai.md) 기준입니다.
- 주문 트러블슈팅: [회원 잠금 테스트 오판](troubleshooting/order-member-lock-test-false-pass.md) · [잠금 전 선조회](troubleshooting/order-stale-entity-before-lock.md) · [결제 후 장바구니 정리](troubleshooting/order-paid-cart-cleanup.md).
- 결제 트러블슈팅: [지연 큐 복구](troubleshooting/payment-delay-queue-recovery.md) · [포인트 gap lock 교착](troubleshooting/payment-point-gap-lock-deadlock.md) · [스케줄러 자동 설정](troubleshooting/payment-scheduler-autoconfiguration.md).

## 팀 노션·프론트·제출 자료

| 자료 | 위치 | 관리·남은 작업 |
| --- | --- | --- |
| API 명세 | 팀 노션, 링크 입력 대기 | 각 API 담당자가 메서드·경로·권한·요청·응답·오류·쿠키 계약 갱신 |
| SA | 팀 노션, 링크 입력 대기 | 채빈이 보완, 각 담당자가 실제 구조·연동·운영 구성 근거 제공 |
| AI 개발·활용 기록 | 팀 노션 TASK, 링크 입력 대기 | 작업 담당자가 공통 양식으로 실제 검토·수정·검증 기록 |
| 프론트 실행·인증 연동 | [프론트 실행](../frontend/README.md) · [인증 근거](adr/authentication.md#3-5-쿠키cors와-브라우저-경계) · [배포 검증](deploy-implementation.md#7-3-실서버-검증-2026-10-30) | 프론트 태헌·보안 채빈·배포 준희. 기존 HTTPS·인증 확인 기록과 후속 기능의 브라우저 검증을 구분 |
| 시연 시나리오·PPT·영상 | 링크 입력 대기 | 화면 기준 시나리오·데이터·최종 커밋을 팀이 확정 |

API 명세에 완료 개수를 적을 때는 병합된 엔드포인트와 대조합니다.
시연 주소와 검증된 성능 요약은 루트 README에 연결합니다.

## 작성·검토 순서

1. 템플릿을 복사하고 담당자·관련 요구사항·코드 기준·문서 상태를 적습니다.
2. 실제 코드와 테스트·측정 결과를 확인해 내용을 채웁니다. 미검증 항목은 작성·측정 예정으로 표시합니다.
3. 본문에는 비밀 값·개인정보를 넣지 않고 필요한 요청·로그만 가려서 첨부합니다.
4. 관련 PR에 문서를 포함하고, 다른 도메인 계약이 바뀌면 해당 담당자와 함께 검토합니다.
5. 반영 후 이 목차의 링크·상태와 루트 README의 요약을 갱신합니다.

문서 수보다 요구사항 → 선택 근거 → 구현 → 검증 결과가 이어지는지를 기준으로 검토합니다.


## 프론트 누락 연결 보완

기존 API의 화면 연결을 추가했다: `/membership`의 카드·체험·구독·해지, `/admin/membership-payments`의 결제 시도·환불 운영, `/admin/member-points`의 회원 포인트, `/payments`의 회원 결제 내역, `/points/earnings`의 적립 건, `/products/best`와 `/recipes/rankings`의 순위, `/auto-carts/new`의 여러 메뉴 선택. 운영 플래그 활성화 및 실제 PG 검증 완료를 뜻하지 않는다.

`GET /api/members/me`의 `currentAddress.unavailableReason`은 이용 불가 시 `MART_CLOSED`, `MART_MISMATCH`, `OUTSIDE_SERVICE_AREA`를 반환하고 이용 가능 시 null이다. 저장된 배송지·지정 마트는 조회로 바꾸지 않는다.
