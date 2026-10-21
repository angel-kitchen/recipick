# 컨벤션 요약

코드 작성 시 항상 따르는 핵심 규칙. 상세 설명, 공통 에러 코드 표(COMMON·AUTH), 이슈·PR 템플릿은 `docs/convention.md`를 본다.

## 기본
- Java 17, Spring Boot 3.x, Spring Data JPA, QueryDSL
- 시간은 한국 시간(+09:00), ISO-8601 (`2026-10-14T20:30:00+09:00`)
- 패키지: `domain`(member, product, recipe, order, payment), `global`, `infra`
- 도메인 하위 구조: `controller`, `service`, `repository`, `entity`, `dto/request`, `dto/response`, `exception`, 필요할 때만 `event`, `scheduler`
- 한 도메인에서만 쓰는 코드는 그 도메인 안에, 두 개 이상 도메인이 쓸 때만 `global/common`
- `infra`에는 외부 호출만 두고 업무 판단은 `domain`의 Service가 한다.

## API
- URL: 회원 `/api/...`, 관리자 `/api/admin/...`, 웹훅 `/api/webhooks/...`
- 리소스는 복수형 kebab-case(`/cart-items`), 행위는 하위 경로(`/auto-carts/{id}/confirm`)
- 본인 리소스는 `/me`, 회원 ID는 경로로 받지 않고 토큰에서 꺼낸다.
- 응답: `{success, code, message, data}`, 실패 시 `errors`(입력값 검증 실패만), `timestamp`
- 처리 중 상태는 오류가 아니다: `success: true`, `data.status = IN_PROGRESS`
- 조회·미리보기의 판정 결과(예: 배송 가능 여부)는 상태 필드로 내려주고, 거절 오류는 상태를 바꾸는 요청의 최종 검증에서만 쓴다.
- 목록: 요청 `page`(0부터), `size`(기본 20, 최대 100), `sort`, 응답 `content`, `page`, `size`, `totalElements`, `totalPages`. 장바구니처럼 한 화면에서 전체를 다루는 목록은 페이지 없이 전체 반환(`data`는 목록과 부가 정보를 담은 객체, 페이지 필드 없음)
- 값 형식: 금액은 원 단위 정수, 필요량·판매 단위 수치는 소수 문자열(`"150.5"`), 휴대전화는 숫자만, enum은 UPPER_SNAKE_CASE, JSON 필드는 camelCase

## 인증·권한
- 권한 표기: PUBLIC, MEMBER, MEMBERSHIP, SUPER_ADMIN, MART_ADMIN, WEBHOOK
- 로그인 회원은 `@LoginMember Long memberId`, 관리자는 `@LoginAdmin Long adminId`
- 회원 토큰과 관리자 토큰은 서로의 API를 호출할 수 없다.
- MART_ADMIN은 요청한 마트가 토큰의 `martId`와 같은지 서버에서 검사한다.
- SUPER_ADMIN 전용 API는 `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- 다른 회원 소유 리소스는 403 대신 그 도메인의 404 코드로 응답한다.

## 도메인 간 호출
- 다른 도메인의 Entity·Repository를 직접 쓰지 않고 그 도메인의 Service를 호출한다.
- Service끼리 직접 주입하지 않는다. 여러 도메인을 함께 처리하면 Facade에서 조립한다.
- 강한 정합성이 필요한 변경(주문·결제·재고)은 Facade가 한 트랜잭션에서 동기 호출하고, `REQUIRES_NEW`는 쓰지 않는다.
- `ApplicationEvent`는 알림·통계·로그처럼 일시적 불일치가 허용되는 후처리에만 쓴다.

## 네이밍
- 클래스 `{대상}{역할}` PascalCase (`RecipeService`, `OrderAdminController`)
- 메서드는 동사로 시작. 조회는 1건 `get`, 여러 건 `find`·`search`, 존재 여부 `exists`
- 요청 DTO `{대상}{행위}Request`, 응답 DTO `{대상}{용도}Response`
- 테이블·컬럼은 단수형 snake_case(`orders`만 예외), 시각 `_at`, 날짜 `_date`
- CANCELED(L 하나), boolean은 형용사·과거분사(`deleted`, `active`)

## Entity
- setter 금지. 상태 변경은 규칙을 검사하는 엔티티 메서드로 한다.
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 생성은 정적 팩토리나 생성자 위 `@Builder`
- enum은 `@Enumerated(EnumType.STRING)` + `@Column(columnDefinition = "varchar(20)")`(MySQL enum·CHECK 생성 방지), 연관관계는 `fetch = LAZY`
- 금액 `Long`, 개수 `int`, 필요량·판매 단위 수치 `BigDecimal`
- 시각은 `LocalDateTime`(한국 시간)으로 저장하고, API에는 `JacksonConfig`가 `+09:00`을 붙여 내보낸다.
- `created_at`, `updated_at`은 `BaseTimeEntity` 상속

## DTO·Controller
- DTO는 `record`, 엔티티를 응답으로 내보내지 않는다.
- 입력 검증은 `@Valid` + jakarta validation, 비즈니스 검증은 Service
- Controller는 `ResponseEntity<ApiResponse<T>>`를 반환하고 로직을 두지 않는다.

## 트랜잭션·동시성
- 클래스에 `@Transactional(readOnly = true)`, 변경 메서드에만 `@Transactional`
- 외부 호출(PortOne, AI)은 DB 트랜잭션 밖에서 한다. 락을 잡은 상태에서 외부 호출 금지. 흐름은 `내부 상태 저장 → COMMIT → 외부 호출 → 결과 반영 트랜잭션`
- 낙관적 락(`@Version`) 금지. 동시 변경이 겹치는 자원은 비관적 락(`PESSIMISTIC_WRITE`)으로 잠근 뒤 검증·변경한다. `NOWAIT` 금지
- 잠금 전용 조회는 `findByIdForUpdate`처럼 이름으로 구분한다.
- 락 순서 고정: `PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고`, 같은 종류 여러 행은 PK 오름차순
- 신규 행 중복 생성은 UNIQUE 제약으로 최종 방어한다.
- 락 대기 시간 초과는 `COMMON-010`(409), Idempotency-Key 충돌·처리 중은 `COMMON-006`(409)
- 벌크 UPDATE는 Auditing을 거치지 않으므로 `updated_at`을 함께 갱신한다.

## 같은 요청 반복(멱등성)
- 같은 요청이 다시 오면 처음 결과를 그대로 성공으로 반환한다. 409는 현재 상태와 맞지 않을 때만 쓴다.
- 주문 생성: `Idempotency-Key` 헤더 → `ORDERS.idempotency_key`(VARCHAR(64), 소문자 정규화), 본문 SHA-256 → `request_hash`
  - 같은 키·같은 해시면 기존 주문 반환(재고 차감·결제 생성 반복 없음), 같은 키·다른 해시면 `COMMON-011`
  - 이 확인은 결제 대기 주문 제한보다 먼저 한다.
- 환불도 `Idempotency-Key`로 중복 처리를 막는다.
- 웹훅은 `webhook_id`로 한 번만 처리하고, 이미 처리한 웹훅은 200을 반환한다.

## 예외·로그
- 비즈니스 오류는 `throw new BusinessException(XxxErrorCode.CODE)`로만 던진다.
- 도메인별 ErrorCode enum이 `ErrorCode` 인터페이스를 구현한다.
- 에러 코드 형식 `{접두어}-{세 자리 번호}`, 접두어 안에서 001부터, 지운 번호는 재사용하지 않는다.
- HTTP 상태는 400~409, 500만 쓴다.
  - 400: 입력은 맞지만 규칙 위반
  - 403: 자격 없음
  - 404: 대상 없음, 다른 회원 소유
  - 409: 현재 상태와 맞지 않음, 중복
  - 500: 외부 서비스 오류(코드로 구분)
- 접두어: member(MEMBER, ADDRESS, REGION, MEMBERSHIP), product(PRODUCT, CATEGORY, MART, STOCK), recipe(RECIPE, INGREDIENT, AUTO_CART, AI), order(CART, ORDER), payment(PAYMENT, REFUND, POINT, BILLING)
- message는 사용자에게 그대로 보여줄 수 있는 문장으로 쓴다.
- catch 후 무시 금지. 외부 API 실패는 WARN, 예상 못한 예외는 ERROR + 스택
- 비밀번호, 토큰, 빌링키, 카드 정보, 휴대전화 원문은 로그에 남기지 않는다.

## 테스트
- 메서드 이름은 영어, `@DisplayName`은 한국어 문장, 본문은 `// given`, `// when`, `// then`
- 계산 로직은 단위 테스트, 재고 변경·결제 확정은 동시성 테스트
- PR 전 `./gradlew test` 통과

## 포맷
- 들여쓰기 4칸, 한 줄 120자, import 와일드카드 금지
- 주석은 "왜"를 적고 "무엇"은 적지 않는다.

## Git
- 브랜치 `{prefix}/{domain}-{작업}` (feature, fix, refactor, test, docs, chore, style, hotfix). `global`·`infra` 작업은 domain 자리에 `global`
- 커밋 `{type}: {한국어 요약} (#{이슈번호})` (feature, fix, refactor, test, docs, chore, style), 커밋 하나에 한 가지 변경
- `main`, `dev`에 직접 push하지 않는다.
- ERD, 공통 규칙, 에러 코드를 바꿀 때는 문서를 먼저 고친다.
