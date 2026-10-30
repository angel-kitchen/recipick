# Recipick

레시피에 필요한 재료를 이용 마트의 상품으로 구성해 장바구니와 주문으로 연결하는 지역 기반 장보기 서비스입니다.
배송지의 법정동으로 이용 마트를 정하고, 상품의 판매 단위·가격·재고를 기준으로 구매 수량을 계산합니다.

이 저장소는 Spring Boot 백엔드입니다. 기능 범위는 [요구사항](docs/requirement.md), 상세 근거와 검증 자료는
[문서 목차](docs/README.md)에서 확인할 수 있습니다. 요구사항의 1·2·3차 구분은 구현 계획이며, 완료 여부는 병합된 코드·PR과 검증 결과를 기준으로 기록합니다.

## 주요 기능과 담당

| 도메인 | 기능 범위 | 담당 |
| --- | --- | --- |
| 회원 | 가입·인증, 관리자, 배송지, 멤버십, 감사 기록 | 채빈 |
| 상품 | 마트·카테고리·상품·재고, 재료 연결 | 태헌 |
| 레시피 | 레시피·찜·자동 구성, AI 장보기 | 보민 |
| 주문 | 장바구니·담기 이력, 주문·마트 처리 | 준희 |
| 결제 | 카드 결제·환불·웹훅, 포인트·멤버십 결제 | 정윤 |

## 기술과 도입 의도

아래는 현재 코드에서 사용하는 기술의 역할입니다. 대안 비교·트레이드오프·검증 결과는 각 담당자가 상세 근거 문서에 작성합니다.

| 기술 | 버전·기준 | 도입 의도 |
| --- | --- | --- |
| Java / Spring Boot | 17 / 3.5.16 | HTTP API·검증·트랜잭션 처리를 공통 실행 환경으로 구성 |
| Spring Data JPA / Hibernate | Spring Boot 관리 버전 | 엔티티와 트랜잭션으로 상태 변경을 처리하고 필요한 행에 비관적 잠금 적용 |
| QueryDSL | 5.1.0 Jakarta | 검색 조건·필터·정렬 조합을 타입이 있는 쿼리로 작성 |
| MySQL | 8.4 | 주문·결제·재고의 관계·UNIQUE 제약·동일 DB 트랜잭션 관리 |
| Redis | 7.4 | 리프레시 세션과 로그인 실패·잠금 정보의 만료 및 원자적 갱신 |
| Spring Security / JJWT | JJWT 0.13.0 | JWT 인증과 회원·관리자·마트 권한 검사 |
| PortOne | V2 연동 | 외부 카드 결제 조회·취소 연동 |
| JUnit / Mockito / MockMvc / Testcontainers | Gradle 의존성 기준 | 단위 검증과 실제 MySQL·Redis 기반 통합·동시성 검증 |
| Actuator / Micrometer Prometheus | Spring Boot 관리 버전 | 내부 관리 포트에서 상태·지표 수집 경로 제공 |
| Docker Compose / GitHub Actions | 저장소 설정 기준 | 로컬 DB 실행 구성 공유 및 PR 자동 테스트 |

정확한 의존성은 [build.gradle](build.gradle), 자동 검증은 [CI 설정](.github/workflows/ci.yml)을 따릅니다.
현재 상태·지표 공개 규칙은 [모니터링 문서](docs/monitoring.md)에 정리되어 있습니다.
배포의 [선택 근거](docs/deploy-strategy.md)·[구현 기록](docs/deploy-implementation.md)·[운영 절차](deploy/README.md),
성능의 [부하 테스트](docs/performance/load-test.md)·[병목 개선](docs/performance/bottleneck.md)·[캐싱](docs/performance/caching.md) 보고서가 있습니다.
프론트 연동·HTTPS 검증 결과와 제출용 성능 수치 요약은 담당자 확인 후 갱신합니다.

## 구조

```text
src/main/java/com/recipick
├─ domain
│  ├─ member
│  ├─ product
│  ├─ recipe
│  ├─ order
│  └─ payment
├─ global       # 보안·설정·응답·예외·감사 공통 계약
└─ infra        # PortOne·Redis 등 외부 시스템 연결
```

요청은 Controller에서 받아 Service로 처리하고, 여러 도메인을 조립하는 흐름은 Facade가 트랜잭션 경계를 관리합니다.
다른 도메인의 정보와 변경은 공개 Service 계약을 통해 요청합니다.
선택 이유와 실제 요청 흐름은 [아키텍처 근거](docs/adr/layered-architecture.md), 상세 구조·호출 규칙은 [컨벤션](docs/convention.md#도메인-간-호출), 테이블 구조는 [ERD](docs/erd.dbml)를 확인해 주세요.

## 로컬 실행

Java 17, Docker와 Docker Compose를 준비합니다. 아래 명령은 macOS·Linux·Git Bash 기준이며 저장소 루트에서 실행합니다.

### 1. 로컬 설정 준비

```bash
cp .env.example .env
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

- `.env`의 MySQL DB명·사용자·비밀번호와 `application-local.yml`의 datasource 값을 맞춥니다.
- 포트를 바꾸면 datasource URL과 Redis 접속 포트도 함께 맞춥니다. `.env`는 Docker Compose용이며 앱 설정에 자동으로 주입되지 않습니다.
- PortOne 실제 연동을 확인할 때는 `application-local.yml`의 API Secret·store-id·channel-key를 본인 테스트 환경에 맞게 설정합니다.
- 두 로컬 설정 파일은 Git에서 제외됩니다. 실제 인증 정보는 예시 파일이나 문서에 옮기지 않습니다.

### 2. MySQL·Redis 실행

```bash
docker compose up -d
docker compose ps
```

DB와 Redis가 정상 실행 중인지 확인합니다. 기본 포트는 MySQL 3306, Redis 6379입니다.

### 3. JWT 키 설정과 앱 실행

실행하는 터미널에 JWT 키를 설정합니다. 아래 예시는 OpenSSL이 설치된 환경에서 로컬 키를 생성합니다.

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
./gradlew bootRun --args='--spring.profiles.active=local'
```

키는 실행 환경의 비밀 값으로 관리하며 출력·커밋하지 않습니다. 키를 바꾸면 이전 키로 발급한 토큰은 사용할 수 없습니다.
기본 앱 주소는 `http://localhost:8080`입니다. 공개 API로 연결을 확인할 수 있습니다.

```bash
curl -i http://localhost:8080/api/categories
```

로컬 프로필은 예시 설정의 `ddl-auto: update`와 초기 SQL을 사용합니다. 초기 지역·마트는
[data.sql](src/main/resources/data.sql), 로컬 관리자 시드는 [data-admin-local.sql](src/main/resources/data-admin-local.sql)을 참고합니다.
운영 관리자·환경변수·배포용 DB 변경은 [운영 절차](deploy/README.md)를 확인하고 배포 담당자와 적용 범위를 맞춥니다.

프론트를 별도 로컬 출처에서 실행할 때는 `CORS_ALLOWED_ORIGINS`에 해당 출처를 지정합니다.
실제 배포 주소·쿠키 정책·프론트 실행법은 담당자 합의 후 추가합니다.

### 4. 테스트

```bash
./gradlew test
```

통합 테스트는 Testcontainers가 테스트 전용 MySQL·Redis를 실행하므로 Docker가 켜져 있어야 합니다.
로컬 개발 DB와 테스트 DB는 분리됩니다. 결과는 `build/reports/tests/test/index.html`에서 확인합니다.
GitHub Actions는 `dev`·`main` 대상 PR과 해당 브랜치 push에서 테스트를 실행합니다.

## 성능 결과 요약

측정 후 환경·커밋·보고서 링크와 함께 채웁니다. 목표와 실제 측정값은 구분합니다.

| 주제 | 환경·데이터 | Before | After | 결과 근거 |
| --- | --- | --- | --- | --- |
| 상품 검색·쿼리 개선 | 보고서에 기록 | 제출용 요약 확인 대기 | 제출용 요약 확인 대기 | [병목 개선](docs/performance/bottleneck.md) |
| 캐싱 | 보고서에 기록 | 제출용 요약 확인 대기 | 제출용 요약 확인 대기 | [캐싱](docs/performance/caching.md) |
| 부하 테스트 | 보고서에 기록 | 제출용 요약 확인 대기 | 제출용 요약 확인 대기 | [부하 테스트](docs/performance/load-test.md) |
| 호출 제한 | 담당자 확인 대기 | 확인 대기 | 확인 대기 | 결과 취합 대기 |

측정용 데이터의 규모와 생성 방법은 [성능 시드 문서](docs/performance/seed-data.md)를 참고합니다.
위 표에는 측정 완료한 결과만 반영합니다.

## 시연과 제출 자료

| 자료 | 링크·상태 |
| --- | --- |
| 서비스·프론트 접속 주소 | 배포 후 작성 |
| API 명세·팀 노션 | 팀 공유 링크 입력 대기 |
| SA·배포 구성도 | 팀 공유 링크 입력 대기 |
| PPT | 작성 대기 |
| 시연 영상(2~4분) | 촬영 후 작성 |
| 최종 소스·제출 기준 커밋 | 릴리스 시 작성 |

## 문서 작성과 협업

[문서 목차와 담당](docs/README.md)에서 작성 위치·상태를 확인합니다.
기능 요구사항·ERD·호출 규칙이 바뀌면 해당 문서와 구현을 함께 검토합니다.
브랜치·커밋·PR 규칙은 [팀 컨벤션](docs/convention.md)을 따릅니다.
