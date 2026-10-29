# 레시피 장보기 개발 컨벤션·공통 에러 코드

## 기준

API 명세는 각자 형식으로 쓰되, 아래 공통 규칙과 에러 코드 체계는 모두 따릅니다. 기준 ERD는 '레시피 장보기 ERD 최종본(마트 통합안)'에 배송지 목록·관리자 감사 로그·탈퇴 후 체험 차단을 반영한 33개 테이블입니다.

| 항목 | 따르는 쪽 | 결정 |
| --- | --- | --- |
| Base URL, 응답·에러 형식 | 결정 | `/api`, `{success, code, message, data}` |
| 같은 요청 반복 | 결정 | 같은 요청은 처음 결과를 그대로 반환, 같은 키에 다른 요청은 409, 이미 처리한 웹훅은 200 |
| 권한 표기 | 컨벤션 v2 | PUBLIC, MEMBER, MEMBERSHIP, SUPER_ADMIN, MART_ADMIN, WEBHOOK |
| 인증 | 결정 | JWT(액세스 30분), 리프레시 토큰 14일(HttpOnly 쿠키, Redis 저장) |
| 시간 | 결정 | 한국 시간(+09:00) |
| 패키지 | 결정 | `domain`(member, product, recipe, order, payment), `global`, `infra` |
| HTTP 상태 | 컨벤션 v2 | 400~409, 500만 사용 |
| 브랜치·PR | 컨벤션 v2 | `{prefix}/{domain}-{작업}`, 승인 2개 |
| 동시성 | 결정 | 비관적 락, 조건부 UPDATE |

## 공통 API 규칙

각자 API 명세를 쓸 때 URL, 권한, 응답 모양은 이 규칙으로 통일합니다. 그래야 프론트와 다른 도메인이 한 가지 방식으로 호출하고 오류를 처리할 수 있습니다.

### URL

- 회원 API `/api/...`, 관리자 API `/api/admin/...`, PortOne 웹훅 `/api/webhooks/...`
- 리소스는 복수형 kebab-case(`/cart-items`, `/auto-carts`), 행위는 하위 경로(`/auto-carts/{id}/confirm`)
- 본인 리소스는 `/me`(`/members/me`), 경로의 회원 ID는 받지 않고 토큰에서 꺼냅니다.
- 마트 콜백 API는 두지 않습니다. 마트 진행 상태는 MART_ADMIN이 관리자 API로 바꿉니다.

### 권한 표기

| 표기 | 의미 |
| --- | --- |
| PUBLIC | 토큰 없이 호출 |
| MEMBER | 회원 토큰, 본인 데이터만 |
| MEMBERSHIP | 회원 토큰 + 활성 멤버십(체험 포함) |
| SUPER_ADMIN | 전체 관리자 |
| MART_ADMIN | 소속 마트 관리자: 자기 마트의 상품 등록·수정·삭제, 가격·재고·판매 상태 변경, 주문 수락, 결제 취소(거절), 배송 상태 변경. 결제·환불 처리는 SUPER_ADMIN과 시스템 |
| WEBHOOK | PortOne 서명 검증 |

관리자 토큰에는 `role`과 `martId`를 넣고, MART_ADMIN은 요청한 마트가 `martId`와 같은지 서버에서 검사합니다.

### 인증·인가

- JWT는 Security Filter에서 한 번만 파싱하고, 인증 정보는 SecurityContext Principal(주체 유형, ID, role, martId)에 담습니다.
- 회원 토큰은 인증 때 DB의 `MEMBER.withdrawn_at`을 확인해 탈퇴 회원의 접근을 거절합니다. `global/security`의 공통 인터페이스(예: `MemberStatusChecker`)를 member가 구현하며, 인증 필터는 도메인 Entity·Repository를 직접 참조하지 않습니다.
- 탈퇴 계정의 로그인·재발급도 거절합니다. 탈퇴 상태 DB 변경과 Redis 세션 삭제는 원자적이지 않으므로 DB 검사를 차단의 최종 기준으로 삼고, 실패한 세션 정리는 재시도합니다.
- 로그인 회원은 `@LoginMember Long memberId`, 로그인 관리자는 `@LoginAdmin Long adminId`로 받습니다. 회원 토큰과 관리자 토큰은 서로의 API를 호출할 수 없습니다.
- 관리자 토큰은 `type=ADMIN`, `adminId`, `role`(SUPER_ADMIN, MART_ADMIN), MART_ADMIN만 `martId`를 담습니다.
- `/api/admin/**`는 SUPER_ADMIN, MART_ADMIN 모두 Security를 통과하고, SUPER_ADMIN 전용 API(결제·환불 관리자 API 등)는 `@PreAuthorize("hasRole('SUPER_ADMIN')")`를 추가합니다.
- 공개 경로(permitAll): `/api/auth/signup`, `/api/auth/login`, `/api/auth/reissue`, `/api/admin/auth/login`, `/api/webhooks/**`, `/api/config/**`, `/api/service-area/check`(서비스 가능 지역 확인), 각 도메인이 정한 비로그인 조회 경로
- `/api/config/**`에는 프론트에 공개 가능한 값(storeId, channelKey)만 반환하고, API Secret·Webhook Secret은 절대 반환하지 않습니다.
- 리프레시 토큰은 응답 본문이 아니라 `HttpOnly; Secure; SameSite=Strict` 쿠키로 주고받고, Redis에 저장합니다. 같은 계정 로그인 3회 연속 실패 시 10분 동안 로그인을 막습니다.
- 회원 리프레시 토큰에는 `refreshVersion`을 넣고 재발급 시 `MEMBER.refresh_token_version`과 비교합니다. 비밀번호 해시와 버전은 같은 DB 트랜잭션으로 갱신하며, 버전이 없는 기존 토큰은 0으로 처리합니다. 액세스 토큰 검증에는 이 버전을 적용하지 않습니다.
- 비밀번호 변경의 Redis 세션 조회·삭제는 DB 회원 잠금 밖에서 수행합니다. 변경 전 확보한 키만 커밋 뒤 정리하며 삭제 실패·늦은 저장이 있어도 DB 버전으로 이전 세션을 거절합니다. 상세 실패 정책은 [비밀번호 변경 API](api/member-password-change.md)를 따릅니다.
- Security Filter에서 발생한 401·403은 `GlobalExceptionHandler`를 거치지 않으므로, `AuthenticationEntryPoint`와 `AccessDeniedHandler`에서 공통 Wrapper로 직접 응답합니다.
- PortOne 웹훅은 JWT 대신 서명 검증을 사용합니다. 서명 검증에 원문 body가 필요하므로 `/api/webhooks/**`는 request body를 먼저 읽는 로깅 필터에서 제외합니다.
- CORS는 `global/config`에서 한 곳에서 설정하며, 허용 헤더에 `Authorization, Content-Type, Idempotency-Key`를 포함합니다. 리프레시 토큰 쿠키를 주고받기 위해 `allowCredentials`를 켜고, 허용 Origin은 와일드카드(*) 없이 프론트 주소만 지정합니다.

### 응답

```json
// 성공
{ "success": true, "code": "OK", "message": "요청이 처리되었습니다.", "data": { } }

// 실패
{
  "success": false,
  "code": "COMMON-001",
  "message": "입력값이 올바르지 않습니다.",
  "errors": [ { "field": "quantity", "reason": "1 이상이어야 합니다." } ],
  "timestamp": "2026-10-14T20:30:00+09:00"
}
```

- `errors`는 입력값 검증 실패일 때만 채우고, 나머지는 빈 배열입니다.
- 처리 중인 상태(예: 결제 결과 확인 중)는 오류가 아니므로 `success: true`, `data.status = IN_PROGRESS`로 보냅니다. 오류로 보내면 프론트가 실패로 처리하기 쉽습니다.
- 조회·미리보기 단계의 판정 결과(예: 주문서의 배송 가능 여부)는 오류가 아니라 상태 필드로 내려줍니다. 거절 오류는 주문 생성처럼 상태를 바꾸는 요청의 최종 검증에서만 씁니다.
- 클라이언트는 HTTP 상태가 아니라 `code`로 분기합니다.

### 값 형식

| 항목 | 규칙 | 예 |
| --- | --- | --- |
| 시간 | ISO-8601, 한국 시간 | `2026-10-14T20:30:00+09:00` |
| 날짜 | ISO-8601 | `2026-10-14` |
| 금액 | 원 단위 정수 | `3900` |
| 필요량·판매 단위 수치 | 소수 문자열 | `"150.5"` |
| 휴대전화 | 숫자만, 하이픈 없음 | `01012345678` |
| enum | 대문자 스네이크 | `PENDING_PAYMENT` |
| JSON 필드 | 카멜 케이스 | `amountPerServing` |

### 목록

- 요청: `page`(0부터), `size`(기본 20, 최대 100), `sort`(예: `createdAt,desc`)
- 응답 `data`: `content`, `page`, `size`, `totalElements`, `totalPages`
- 한 화면에서 전체를 보고 선택해야 하는 목록(장바구니)은 페이지 없이 전체를 반환합니다. `data`는 목록과 부가 정보를 담은 객체로 반환하고(예: `{items, hasPreviousMartItems}`), 페이지 필드(`page`, `size`, `totalElements`, `totalPages`)는 넣지 않습니다.

### 같은 요청 반복

- 같은 요청이 다시 오면 처음 처리한 결과를 그대로 성공으로 돌려줍니다. 409는 요청이 현재 상태와 맞지 않을 때만 씁니다.
- 주문 생성은 `Idempotency-Key` 헤더를 받아 `ORDERS.idempotency_key`에 저장하고, 요청 본문으로 계산한 `request_hash`(SHA-256)를 함께 저장합니다. 같은 키와 같은 해시가 다시 오면 재고 차감·결제 생성을 다시 하지 않고 기존 주문의 현재 상태를 돌려주며, 같은 키에 해시가 다르면 `COMMON-011`(409)입니다. 이 확인은 결제 대기 주문 제한보다 먼저 합니다. `idempotency_key`는 `VARCHAR(64)`이고 소문자로 정규화해 저장하며, 64자를 넘으면 `COMMON-001`입니다.
- 결제 확정은 결제 상태로 판단합니다(이미 PAID면 같은 결과 반환).
- 환불(주문 환불, 멤버십 즉시 해지 환불)은 `Idempotency-Key` 헤더를 받아 `idempotency_key`에 저장합니다. 같은 키가 다시 오면 PG 호출·환불 기록·상태 변경을 다시 하지 않고 기존 결과를 돌려줍니다.
- 같은 키의 첫 요청이 아직 처리 중이거나, 같은 대상에 결과 확인 중인 환불이 있는데 다른 키로 요청하면 `COMMON-006`(409)입니다. 명확한 실패가 확정된 뒤 다시 시도할 때는 새 키를 씁니다.
- PortOne 웹훅은 `webhook_id`로 한 번만 처리하고, 이미 처리한 웹훅이 다시 오면 409가 아니라 200을 돌려 재전송을 멈춥니다.

## Code Convention

Java 17, Spring Boot 3.x, Spring Data JPA, QueryDSL 기준입니다. 최상위는 `domain`(업무), `global`(공통 기반), `infra`(외부 시스템) 세 개로 나누고, `domain` 안에 담당 영역별 패키지 5개를 둡니다. 다른 도메인의 Repository는 직접 부르지 않고 그 도메인의 Service를 호출합니다.

### 패키지 구조

```
com.{project}
├─ domain                          // 업무 코드(담당 영역별)
│  ├─ member                       // 회원, 관리자, 지역, 배송지, 멤버십
│  │  ├─ controller
│  │  ├─ service
│  │  ├─ repository
│  │  ├─ entity
│  │  ├─ dto
│  │  │  ├─ request
│  │  │  └─ response
│  │  ├─ event                     // 다른 도메인에 알리는 이벤트(필요할 때만)
│  │  ├─ scheduler                 // 만료·자동 처리 등 정기 작업(필요할 때만)
│  │  └─ exception                 // MemberErrorCode 등
│  ├─ product                      // 카테고리, 상품, 마트, 재고
│  ├─ recipe                       // 레시피, 재료, 찜, 자동 장바구니, AI 기능
│  ├─ order                        // 장바구니, 담기 이력, 주문, 주문 상태 이력
│  └─ payment                      // 결제, 웹훅, 환불, 포인트, 구독 결제·빌링키
│
├─ global                          // 모든 도메인이 쓰는 공통 기반
│  ├─ config                       // Security, JPA Auditing, QueryDSL, Redis, Web(CORS), Swagger, Scheduling
│  ├─ security                     // JWT, 인증 필터, Principal, @LoginMember·@LoginAdmin,
│  │                               // EntryPoint·AccessDeniedHandler, 마트 관리자 권한 검사
│  ├─ filter                       // 요청 로깅 필터(/api/webhooks/** 제외)
│  ├─ audit                        // AdminAuditRecorder와 공통 입력 DTO(구현·저장은 member)
│  ├─ exception                    // ErrorCode, CommonErrorCode, AuthErrorCode, BusinessException, GlobalExceptionHandler
│  ├─ response                     // ApiResponse, ErrorResponse, PageResponse
│  ├─ entity                       // BaseTimeEntity
│  └─ common                       // 정책 상수(결제 대기 5분, 이용료 3,900원. 서비스 반경은 2km 도입 때 추가), 공통 enum, 유틸
│
└─ infra                           // 외부 시스템 연결
   ├─ portone                      // PortOne Client, 웹훅 서명 검증
   ├─ redis                        // 리프레시 토큰, 로그인 실패 횟수 저장
   └─ ai                           // AI API Client
```

- `domain` 아래 다섯 패키지는 모두 `member`와 같은 하위 구조를 씁니다. `event`, `scheduler`는 필요한 도메인만 만듭니다.
- 클래스는 이름만 보고 찾을 수 있게 `{대상}{역할}`로 짓습니다(예: `domain/member/service/MembershipService`, `domain/payment/scheduler/MembershipBillingScheduler`).
- 특정 도메인에서만 쓰는 코드는 그 도메인 안에 두고, 두 개 이상 도메인이 함께 쓸 때만 `global/common`으로 올립니다.
- `infra`에는 외부 호출만 두고, 환불 가능 여부 같은 업무 판단은 `domain`의 Service가 합니다.

| 위치 | 주 담당 |
| --- | --- |
| `domain/member` | 채빈 |
| `domain/product` | 태헌 |
| `domain/recipe` | 보민(AI 기능 포함) |
| `domain/order` | 준희 |
| `domain/payment` | 정윤 |
| `global/security`, `global/exception`, `global/response` | 채빈(변경 시 전체 공유) |
| `infra/portone` | 정윤 |
| `infra/redis` | 채빈 |
| `infra/ai` | 공동 |

### 도메인 간 호출

- 다른 도메인의 Entity·Repository를 직접 조회하지 않고, 해당 도메인의 Service를 통해 정보 조회와 상태 변경을 요청합니다.
- 도메인 Service끼리는 서로 직접 주입하지 않습니다. 둘 이상의 도메인을 함께 처리하는 흐름은 Facade에서 각 도메인 Service를 조립합니다.
- 주문·결제·재고처럼 강한 정합성이 필요한 상태 변경은 Facade가 같은 DB 트랜잭션 안에서 각 도메인 Service를 동기 호출합니다.
- 호출받는 Service는 기존 트랜잭션에 합류하며 `REQUIRES_NEW`를 사용하지 않습니다.
- 재고 복구는 주문 상태 종료와 같은 트랜잭션에서 주문 도메인이 처리합니다.
- PortOne 등 외부 시스템 호출은 DB 트랜잭션 밖에서 수행하고, 결과 확인 후 짧은 DB 트랜잭션으로 내부 상태를 반영합니다.
- `ApplicationEvent`는 알림·통계·로그처럼 일시적인 불일치를 허용할 수 있는 후처리에만 사용합니다. 관리자 감사 로그는 이번 합의에 따라 이벤트가 아닌 동기 기록 인터페이스로 처리합니다.
- 관리자 감사 로그는 `global/audit`의 `AdminAuditRecorder` 인터페이스로 기록하고 구현은 member가 제공합니다. 각 도메인은 다른 도메인의 구현 Service를 직접 주입하지 않고, 작업을 조립하는 Facade 등 트랜잭션 경계에서 공통 인터페이스를 호출합니다.
- 호출 형식은 `record(new AdminAuditCommand(action, targetId, martId, changes))`입니다. `AdminAuditAction`에서 대상 종류를 결정하며, 관리자 ID와 IP는 서버의 인증·요청 정보에서 구합니다.
- 변경 값은 `Map<AdminAuditField, AdminAuditChange>`로 전달합니다. 허용한 필드의 숫자·불리언·enum·날짜/시각만 받으며 문자열·응답 DTO·임의 객체는 거절합니다. enum은 문자열로 바꾸지 않고 enum 값으로 전달합니다.
- 기록 메서드는 쓰기 가능한 바깥 트랜잭션이 필수입니다(`MANDATORY`). 호출하는 쪽에서 실제 변경·새 실행 여부를 판단해 멱등 재전송은 건너뛰고, 개인정보 조회는 요청마다 호출합니다.
- 내부 DB 변경과 감사 기록은 같은 쓰기 가능한 트랜잭션으로 묶고, 기록 실패 시 작업도 롤백합니다. 개인정보가 보이는 관리자 조회도 감사 기록과 함께 커밋한 뒤 결과를 반환하며, 기록 실패 시 결과를 반환하지 않습니다.
- 외부 결제사 호출이 필요한 관리자 작업은 `요청 접수 상태 + 감사 기록 → COMMIT → 외부 호출 → 결과 반영 트랜잭션`으로 나눕니다. 이미 실행된 외부 처리는 DB 롤백으로 취소할 수 없습니다.
- 탈퇴에 필요한 주문·결제·환불·포인트 조회 및 정리는 각 도메인 Service가 Boolean·DTO 등으로 제공하고 회원 Facade가 조립합니다. 실제 연동 전에는 임시 false 응답으로 탈퇴를 허용하지 않습니다.

### 네이밍

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 클래스 | PascalCase, 역할 접미사 | RecipeService, OrderAdminController |
| 메서드 | camelCase, 동사로 시작 | createOrder, confirmPayment |
| 조회 메서드 | 1건 get, 여러 건 find·search, 존재 여부 exists | getRecipe, searchProducts |
| 상수·enum | UPPER_SNAKE_CASE | PENDING_PAYMENT |
| 요청 DTO | {대상}{행위}Request | AutoCartPreviewRequest |
| 응답 DTO | {대상}{용도}Response | RecipeDetailResponse |
| 테이블·컬럼 | 단수형 snake_case(orders만 예외) | recipe_ingredient |
| 시각·날짜 컬럼 | 시각 `_at`, 날짜 `_date`(예외: point_earning.expires_on) | paid_at, billing_date |
| 상태 철자 | CANCELED(L 하나) | canceled_at |
| boolean | 형용사·과거분사 | deleted, active, required |

### Entity

- setter를 두지 않고, 상태 변경은 규칙을 검사하는 메서드로 합니다.
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 생성은 정적 팩토리 메서드나 생성자 위 `@Builder`로 합니다.
- enum은 `@Enumerated(EnumType.STRING)`, 연관관계는 `fetch = LAZY`가 기본입니다.
- enum 컬럼은 `@Column(columnDefinition = "varchar(20)")`처럼 varchar로 매핑합니다. 지정하지 않으면 Hibernate가 MySQL enum 타입이나 CHECK 제약을 만들어, 값을 추가할 때마다 DDL을 바꿔야 합니다.
- 금액 `Long`, 개수 `int`, 필요량·판매 단위 수치 `BigDecimal`을 씁니다.
- `created_at`, `updated_at`은 `BaseTimeEntity` 상속과 JPA Auditing으로 채웁니다. `updated_at`이 없는 `ADMIN_AUDIT_LOG`·`TRIAL_BLOCK`은 이를 상속하지 않고 `@CreatedDate`와 `AuditingEntityListener`로 `createdAt`만 직접 선언합니다. 생성 시각 전용 부모 클래스는 추가하지 않습니다. 감사 로그는 갱신하지 않지만 체험 차단 기록의 만료 시각은 정책에 따라 연장할 수 있습니다.

```java
// 지양: 서비스에서 상태를 직접 바꿈
order.setStatus(OrderStatus.CANCELED);

// 권장: 엔티티가 규칙을 검사하고 바꿈
public void cancel(CancelReasonCode reasonCode, String reasonDetail) {
    if (!this.status.isCancelable()) {
        throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELABLE);
    }
    this.status = OrderStatus.CANCELED;
    this.canceledAt = LocalDateTime.now();
    this.cancelReasonCode = reasonCode;
    this.cancelReasonDetail = reasonDetail;
}
```

### DTO·Controller

- DTO는 `record`, 엔티티를 응답에 그대로 내보내지 않습니다.
- 입력 검증은 `@Valid`와 jakarta validation, 비즈니스 검증은 Service에서 합니다.
- Controller는 `ResponseEntity<ApiResponse<T>>`를 반환하고 로직을 두지 않습니다.
- 로그인 회원은 `@LoginMember Long memberId`, 로그인 관리자는 `@LoginAdmin Long adminId`로 받습니다.

```java
// 지양: 엔티티 반환, 검증 없음
@PostMapping("/auto-carts/preview")
public AutoCartRequest preview(@RequestBody Map<String, Object> body) { ... }

// 권장
@PostMapping("/auto-carts/preview")
public ResponseEntity<ApiResponse<AutoCartPreviewResponse>> preview(
        @LoginMember Long memberId,
        @Valid @RequestBody AutoCartPreviewRequest request) {
    return ResponseEntity.ok(ApiResponse.success(autoCartService.preview(memberId, request)));
}
```

### 트랜잭션·동시성

- 클래스에는 `@Transactional(readOnly = true)` 를 기본으로 적용하고, 상태 변경 메서드에는 `@Transactional`을 적용합니다. 감사 기록을 함께 저장하는 관리자 조회의 바깥 트랜잭션도 쓰기 가능하도록 선언합니다. 안쪽 기록 메서드의 선언만으로 이미 시작된 readOnly 트랜잭션을 바꾸려 하지 않습니다.
- PortOne, AI 등 외부 시스템 호출은 DB 트랜잭션 밖에서 수행하고, 외부 결과를 받은 뒤 짧은 DB 트랜잭션으로 내부 상태를 반영합니다.
- 낙관적 락(`@Version`)은 쓰지 않습니다. 재고·포인트 잔액·주문·결제·마트 상태처럼 동시 요청이 겹칠 수 있는 핵심 변경은 **비관적 락(`PESSIMISTIC_WRITE`, `SELECT ... FOR UPDATE`)으로 대상 행을 잠근 뒤** 현재 상태를 검증하고 변경합니다.
- 여러 자원을 함께 잠그는 트랜잭션은 **락 순서를 고정**합니다. 회원 잠금이 없는 기존 결제 흐름은 `PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고`를 따르며, 회원을 포함하면 `MEMBER → PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고` 순서로 기존 행 잠금을 획득합니다. 같은 종류의 여러 행은 PK 오름차순으로 잠급니다. 이는 기존 행의 잠금 획득 순서이며 신규 주문·결제 INSERT 순서를 일률적으로 바꾸는 규칙은 아닙니다.
- 탈퇴·신규 주문 생성·멤버십 지급·배송지 변경 등 회원 행 잠금이 필요한 흐름은 해당 트랜잭션의 첫 DB 작업으로 회원 행을 잠근 뒤 탈퇴 여부를 확인합니다. 같은 트랜잭션에서 잠금 전에 일반 조회로 스냅샷을 만들거나 회원 엔티티를 미리 로딩하지 않습니다.
- 주문 생성의 회원 잠금은 같은 멱등 키의 기존 주문 조회보다 앞에 둡니다. 요청 형식 검증·해시 계산 등 DB를 사용하지 않는 처리는 잠금 전에 수행할 수 있습니다.
- 탈퇴한 회원은 `MemberService`의 일반·잠금 조회에서 `MEMBER_NOT_FOUND`로 처리합니다. 인증 단계의 거절과 별개로 상태 변경 진입점에서도 확인합니다.
- 다른 도메인에서 엔티티 반환 없이 회원을 잠글 때는 `MemberService.lockActiveMember(memberId)`를 호출합니다. 쓰기 가능한 바깥 트랜잭션의 첫 DB 작업으로 호출하며, 메서드 반환 후에도 바깥 트랜잭션 종료까지 잠금이 유지됩니다.
- 회원 토큰의 DB 상태 검사는 `MemberStatusChecker.requireActive`로 수행합니다. 탈퇴·없는 회원은 인증·재발급에서 `AUTH-011`로 거절하며, 공개 API와 관리자 토큰에는 회원 상태 조회를 적용하지 않습니다.
- 탈퇴는 회원 잠금 후 진행 중 주문·미해결 결제와 환불·활성 멤버십을 확인합니다. 일반 조회만으로 이 상태를 검사하려면 탈퇴 제한 상태를 새로 만드는 관련 흐름도 같은 회원 잠금 규칙에 참여해야 합니다. 상태를 종료하는 모든 처리에 일괄적으로 회원 잠금을 추가하지 않고, 추가가 필요한 경로는 담당 도메인과 잠금 순서를 확인합니다.
- 장바구니의 이전 마트 상품 일괄 삭제와 담기는 같은 트랜잭션에서 처리하며, 회원의 CART_ITEM 행을 id 오름차순으로 잠근 뒤 진행합니다.
- 주문·결제 생성처럼 아직 존재하지 않는 신규 엔티티에는 비관적 락을 적용할 수 없으므로, 기존에 존재하는 공유 자원에만 잠금을 적용하고 신규 행의 중복 생성은 UNIQUE 제약으로 최종 방어합니다.
- ORDERS에는 `pending_member_id`와 (member_id, idempotency_key) 두 UNIQUE가 있습니다. 충돌 예외가 나면 Facade에서 롤백된 트랜잭션 밖에서 키로 다시 조회합니다. 행이 있으면 기존 주문을 반환하고, 없으면 결제 대기 중복 오류로 처리합니다.
- **락을 잡은 상태에서 PortOne·AI 등 외부 시스템을 호출하지 않습니다.** 외부 호출이 필요한 흐름은 `내부 상태 저장 → COMMIT → 외부 호출 → 결과 반영 트랜잭션`으로 나눕니다.
- `NOWAIT`은 사용하지 않습니다. 경합한 요청은 앞선 트랜잭션이 잠금을 해제할 때까지 대기한 뒤 최신 상태를 다시 확인합니다. 이는 재고 N개에 동시 요청 M건이 들어왔을 때 락 경합 자체로 요청을 즉시 실패시키지 않고, 실제 남은 재고만큼 정상 처리하기 위함입니다.
- 비관적 락 대기 시간이 초과되면 `409 COMMON-010`으로 응답합니다. `COMMON-006`은 Idempotency-Key 및 이미 처리 중인 동일 요청 충돌에만 사용합니다.
- **락 대기 시간은 팀 공통값으로 설정합니다.**
    - 권장안: **5초**
    - 최종 값은 팀 합의 후 DB 환경 설정에 동일하게 적용합니다.
    - `jakarta.persistence.lock.timeout` 힌트의 양수 값은 DB·Dialect에 따라 적용되지 않을 수 있으므로 의존하지 않습니다.
- 잠금 전용 Repository 메서드는 `findByIdForUpdate`처럼 이름으로 용도를 명확히 드러내며 일반 조회 메서드와 구분합니다.
- 대량 배치는 행별 비즈니스 처리나 감사 이력이 필요 없는 단순 일괄 변경에 한해 벌크 UPDATE를 사용할 수 있습니다. 벌크 UPDATE는 JPA Auditing을 거치지 않으므로 `updated_at`을 함께 갱신합니다.
- 포인트 만료처럼 잔액 변경·상태 변경·History 기록을 함께 처리해야 하는 작업은 포인트 도메인의 별도 배치 정책에 따라 정합성을 보장합니다.
- 같은 요청 반복은 비관적 락만으로 처리하지 않고 UK(`pending_member_id`, `webhook_id`, `idempotency_key` 등)과 잠금 후 현재 상태 확인을 함께 사용합니다. 동일 요청이 이미 정상 처리된 경우에는 부작용을 다시 실행하지 않고 기존 결과를 반환합니다.

```java
// 지양: 락 없이 조회하고 저장
Product product = productRepository.findById(id)
        .orElseThrow();

product.deduct(quantity);

// 권장 1) Repository: 잠금 전용 조회를 이름으로 구분
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT p
      FROM Product p
     WHERE p.id = :productId
    """)
Optional<Product> findByIdForUpdate(@Param("productId") Long productId);

// 권장 2) Service: 같은 트랜잭션에서 락 → 검증 → 변경
@Transactional
public void deductStock(Long productId, int quantity) {

    Product product = productRepository
            .findByIdForUpdate(productId)
            .orElseThrow(() ->
                    new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    product.deduct(quantity);
}
```

### 스케줄러

- 스케줄 작업은 `domain/{도메인}/scheduler`에 두고, 스케줄링 활성화는 기존 `global/config/SchedulingConfig` 한 곳에서 관리합니다. 시각을 지정하는 `@Scheduled` 작업에는 `zone = "Asia/Seoul"`을 명시하고, 업무 기준 시각은 주입받은 `Clock`으로 계산합니다.
- Scheduler는 실행 시점과 호출을 담당하고, 업무 검증·상태 변경·트랜잭션은 기존 Facade·Service에서 처리합니다. PortOne 등 외부 호출은 기존 규칙대로 DB 트랜잭션 밖에서 수행합니다.
- **락·중복 처리 방식은 각 도메인 담당자가 작업 특성에 맞게 결정하고 검증합니다.** 여러 서버의 예약 작업 중복 실행과 같은 데이터의 동시 변경을 구분해, 필요한 분산 락·비관적 락·조건부 변경·UNIQUE 등을 선택합니다. 모든 작업에 같은 락 방식이나 공통 락 구현을 일괄 적용하지 않습니다.
- 작업별 실행 주기·기준 시각·처리 대상·만료 또는 보관 기준을 문서화합니다. 락을 사용하면 잠금 대상 또는 작업 이름, 획득·해제 시점, 대기·만료 시간과 만료 중 실행에 대한 대응도 기록합니다. 여러 서버가 공유하는 작업 락은 같은 환경의 동일 작업에 같은 이름을 사용합니다.
- 반복·동시 실행돼도 업무 결과가 중복 반영되지 않도록 상태 확인·조건부 처리·DB 제약·멱등 키 등 필요한 방어를 유지합니다. 락 사용만으로 정확히 한 번 실행된다고 가정하지 않습니다.
- 작업 시작·완료·실패를 확인할 로그를 남기고, 락 미획득·타임아웃·작업 중 예외 발생 시 건너뛰기·재시도·수동 확인 등의 처리와 재처리 기준을 담당자가 정합니다. 일부 처리 후 실패해도 다음 실행에서 남은 대상을 식별할 수 있어야 합니다.
- 적용 전 동시 실행·만료 경계·실패 후 재실행 등 해당 작업에 필요한 시나리오를 검증하고 결과를 관련 PR 또는 도메인 문서에 남깁니다. 단일 인스턴스 실행을 전제로 삼으면 배포 교체 중 중복 기동 여부까지 확인하고 그 전제를 기록합니다.

### 예외·로그

- 비즈니스 오류는 `throw new BusinessException(XxxErrorCode.CODE)`로만 던지고, `GlobalExceptionHandler`가 공통 응답으로 바꿉니다.
- 도메인별 enum이 `ErrorCode` 인터페이스(`getStatus`, `getCode`, `getMessage`)를 구현합니다.
- `catch` 후 무시하지 않습니다. 외부 API 실패는 `WARN`, 예상하지 못한 예외는 `ERROR`와 스택으로 남깁니다.
- 비밀번호, 토큰, 빌링키, 카드 정보, 휴대전화 번호 원문은 로그에 남기지 않습니다.
- 개인정보가 담긴 응답 본문(예: 내 정보 조회의 휴대전화 원문)은 요청 로깅 필터에 남기지 않습니다.
- 감사 로그의 `details`에는 허용한 변경 항목의 전후 값만 넣고 개인정보·비밀번호 해시·토큰·빌링키·비밀키·조회 응답 본문·개인정보 검색어·자유 입력 원문은 넣지 않습니다. 기존 업무 이력 ID를 연결하지 않습니다.
- 감사 로그 IP는 직접 연결받은 주소를 기본으로 합니다. 전달 헤더는 배포 구성에서 지정한 신뢰 프록시가 외부 입력을 정리한 경우에만 사용합니다.
- 체험 차단용 휴대전화는 정규화 후 HMAC-SHA-256으로 계산합니다. HMAC 키는 JWT 키와 분리해 외부 설정으로 관리하고 코드·Git·로그에 남기지 않으며, 키 변경 시 기존 차단 기록 조회 방식을 함께 설계합니다.

### 테스트

- 메서드 이름은 영어, `@DisplayName`은 한국어 문장, 본문은 `// given`, `// when`, `// then`으로 나눕니다.
- 계산 로직(인분 환산, 올림, 포인트 적립, 이용료 환불)은 단위 테스트, 재고 변경과 결제 확정은 동시성 테스트를 둡니다.
- PR 전 `./gradlew test`가 통과해야 합니다.
- 1차 통과 기준 시나리오는 우선 테스트합니다: 재고 N개에 동시 요청 M건이면 성공은 N건, 주문 만료 복구와 결제 성공이 동시에 와도 재고는 한 번만 반영, 같은 웹훅·환불 요청은 한 번만 반영, 회원 취소와 마트 수락이 동시에 오면 한쪽만 성공. 같은 키로 주문 생성이 동시에 와도 주문 1건·재고 1회 차감, 같은 키에 본문이 다르면 409.

### 포맷

- 들여쓰기 공백 4칸, 한 줄 120자, import 와일드카드(`*`) 금지
- IntelliJ 기본 포맷터 + 팀 `.editorconfig`
- 주석은 "왜"를 적고, 코드가 말하는 "무엇"은 적지 않습니다.

## Team Convention

이슈 하나에 브랜치 하나, PR 하나로 작업하고 `dev`에 모은 뒤 차수가 끝나면 `main`으로 올립니다. 브랜치 이름과 리뷰 규칙은 컨벤션 v2를 따릅니다.

### 브랜치

형식: `{prefix}/{domain}-{작업}`

| prefix | 용도 | 만드는 곳 → 합치는 곳 |
| --- | --- | --- |
| feature | 기능 개발 | dev → dev |
| fix | 버그 수정 | dev → dev |
| refactor | 동작 변화 없는 구조 개선 | dev → dev |
| test | 테스트 코드 | dev → dev |
| docs | 문서 수정 | dev → dev |
| chore | 빌드·설정·의존성 | dev → dev |
| style | 포맷 등 로직 없는 변경 | dev → dev |
| hotfix | main 긴급 수정 | main → main, dev |

- `domain`은 도메인 패키지 이름(member, product, recipe, order, payment)을 씁니다. `global`·`infra` 작업은 `global`을, 프론트(`frontend/`) 작업은 `web`을 씁니다.
- 예: `feature/recipe-auto-cart-preview`, `fix/product-stock-negative`, `feature/global-jwt-filter`, `feature/web-cart-page`
- `main`(배포·시연)과 `dev`(통합)에는 직접 push하지 않습니다.

### 커밋

형식: `{type}: {한국어 요약} (#{이슈번호})`

| type     | 쓰는 경우 |
|----------| --- |
| feature  | 기능 추가 |
| fix      | 버그 수정 |
| refactor | 동작이 같은 코드 개선 |
| test     | 테스트 추가·수정 |
| docs     | 문서, API 명세, README |
| chore    | 빌드, 설정, 의존성 |
| style    | 포맷 등 로직 없는 변경 |

예: `feature: 자동 장바구니 미리보기 API 구현 (#23)`

- 커밋 하나에는 한 가지 변경만 담고, 컴파일이 깨진 상태로 커밋하지 않습니다.
- 이슈 없이 진행한 작은 수정(오타 등)은 이슈 번호를 생략합니다.

### 이슈

- 제목: `[도메인] 작업 내용` (예: `[recipe] 레시피 상세 인분 환산`). 프론트는 `[web]`
- 본문: 목적, 할 일 체크리스트, 관련 요구사항 항목, 완료 조건
- 라벨: 도메인, 차수(1차·2차·3차), 종류(feature·bug·refactor)

### PR

- 제목은 커밋 형식과 같고 본문은 템플릿을 씁니다.
- CI 성공, 승인 2개 이상, Merge Conflict 없음을 모두 만족하면 머지합니다.
- `dev`로는 Squash and merge, `main`으로는 Merge commit을 씁니다.
- 머지한 사람이 브랜치를 지우고 이슈를 닫습니다.
- 프론트 PR의 테스트 칸은 `./gradlew test` 대신 `npm run build` 통과와 로컬 화면 확인(필요하면 스크린샷)으로 씁니다.
- 새 API를 쓰는 프론트 PR은 백엔드 PR이 머지된 뒤에 머지합니다. 먼저 열 때는 Draft로 열고 본문에 `선행: #번호`를 적습니다.
- API는 추가만 합니다. 필드나 경로를 지우거나 바꿀 때는 프론트가 먼저 쓰지 않게 바꾸고 다음 PR에서 백엔드를 지웁니다. 배포 순간 새 백엔드와 이전 화면이 함께 돌기 때문입니다.
- 백엔드를 바꾼 PR은 머지 직후 15~30초 동안 운영 API가 502를 낼 수 있어 시연·확인 중에는 머지하지 않습니다. 프론트 통합 배포(#261)가 적용되면 `frontend/`만 바꾼 PR은 앱을 재시작하지 않습니다.

```markdown
## 관련 이슈
- close #23

## 작업 내용
- 자동 장바구니 미리보기 API

## 변경 전/후
| 구분 | 변경 전 | 변경 후 |
| --- | --- | --- |

## 테스트
- [ ] 단위 테스트
- [ ] Postman 확인

## 리뷰 포인트
- 재료 합산 시 단위가 다른 경우 처리
```

### 공유 문서 변경

- ERD, 공통 규칙, 에러 코드를 바꿀 때는 이 문서를 먼저 고치고 팀 채널에 알린 뒤 코드를 바꿉니다.
- 다른 담당 도메인에 영향을 주는 변경(예: `CART_ADD_HISTORY` 기록)은 그 담당자의 확인을 받습니다.
- `global`·`infra` 패키지 변경 PR은 팀원 전체를 리뷰어로 지정합니다.

### 충돌·민감 정보

- Conflict가 있는 PR은 머지하지 않고, 상대방 코드를 임의로 지우지 않고 작성자와 확인해 해결합니다.
- PortOne 키, DB 비밀번호, JWT 시크릿은 코드와 Git에 올리지 않습니다. `application-local.yml`과 Docker용 `.env`는 `.gitignore`에 넣고 예시 파일만 커밋합니다.

## 공통 에러 코드

코드는 `{접두어}-{세 자리 번호}` 형식이고, HTTP 상태는 400~409와 500만 씁니다. 외부 서비스 오류도 500으로 보내되 코드로 구분합니다. 단, AI 기능은 AI 없이 만든 대체 결과가 있으므로 AI 장애·지연·요청 한도 초과를 500으로 보내지 않고 성공 응답(`data.status = FALLBACK`)으로 보냅니다.

### COMMON

| code | HTTP | message | 발생 상황 |
| --- | --- | --- | --- |
| COMMON-001 | 400 | 입력값이 올바르지 않습니다. | @Valid 검증 실패, errors에 필드별 사유 |
| COMMON-002 | 400 | 요청 형식이 올바르지 않습니다. | JSON 파싱 실패, 타입 불일치 |
| COMMON-003 | 400 | 필수 파라미터가 없습니다. | 쿼리·경로 파라미터 누락 |
| COMMON-004 | 404 | 요청한 경로를 찾을 수 없습니다. | 없는 URL |
| COMMON-005 | 405 | 지원하지 않는 메서드입니다. | HTTP 메서드 불일치 |
| COMMON-006 | 409 | 같은 요청을 처리하고 있습니다. 잠시 후 다시 확인해 주세요. | 같은 Idempotency-Key의 첫 요청이 처리 중이거나, 결과 확인 중인 환불이 있는데 다른 키로 요청 |
| COMMON-007 | 400 | Idempotency-Key 헤더가 필요합니다. | 주문 생성·취소·환불 요청에 헤더 누락 |
| COMMON-008 | 500 | 외부 서비스 응답에 문제가 있습니다. | PortOne 오류 응답 (AI는 `FALLBACK` 성공 응답) |
| COMMON-009 | 500 | 외부 서비스 응답이 지연되고 있습니다. | 외부 호출 타임아웃 (AI는 `FALLBACK` 성공 응답) |
| COMMON-010 | 409 | 다른 요청이 같은 자원을 처리하고 있습니다. 잠시 후 다시 시도해 주세요. | 비관적 락 대기 시간 초과 |
| COMMON-011 | 409 | 같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다. | 같은 키에 request_hash가 다른 요청 |
| COMMON-500 | 500 | 서버 내부 오류가 발생했습니다. | 처리하지 못한 예외 |

### AUTH

| code | HTTP | message | 발생 상황 |
| --- | --- | --- | --- |
| AUTH-001 | 401 | 로그인이 필요합니다. | 토큰 없음 |
| AUTH-002 | 401 | 유효하지 않은 토큰입니다. | 서명·형식 오류 |
| AUTH-003 | 401 | 만료된 토큰입니다. | 액세스 토큰 만료(재발급 필요) |
| AUTH-004 | 403 | 접근 권한이 없습니다. | 회원 토큰으로 관리자 API 호출, 관리자 토큰으로 회원 API 호출 |
| AUTH-005 | 403 | 소속 마트의 데이터만 처리할 수 있습니다. | MART_ADMIN이 다른 마트 요청 |
| AUTH-006 | 403 | 전체 관리자만 처리할 수 있습니다. | MART_ADMIN이 SUPER_ADMIN 기능(마트 등록, 결제·환불 관리 등) 호출 |
| AUTH-007 | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | 회원 로그인 실패 |
| AUTH-008 | 401 | 아이디 또는 비밀번호가 올바르지 않습니다. | 관리자 로그인 실패 |
| AUTH-009 | 403 | 비활성화된 관리자 계정입니다. | ADMIN.active = false |
| AUTH-010 | 401 | 웹훅 검증에 실패했습니다. | PortOne 서명 불일치 |
| AUTH-011 | 401 | 다시 로그인해 주세요. | 리프레시 토큰 없음·만료·폐기·재사용 |
| AUTH-012 | 403 | 로그인 시도가 많아 잠시 후 다시 시도해 주세요. | 로그인 3회 연속 실패 후 10분 이내 |

다른 회원의 주문·장바구니처럼 소유자가 다른 리소스는 존재를 숨기기 위해 403 대신 그 도메인의 404 코드로 응답합니다.

### 도메인 에러 코드 작성 규칙

도메인 에러 코드는 각자 API 명세에 함께 적되, 아래 접두어와 상태 기준을 지킵니다.

| 패키지 | 접두어 |
| --- | --- |
| member | MEMBER, ADDRESS, REGION, MEMBERSHIP, AUDIT |
| product | PRODUCT, CATEGORY, MART, STOCK |
| recipe | RECIPE, INGREDIENT, AUTO_CART, AI |
| order | CART, ORDER |
| payment | PAYMENT, REFUND, POINT, BILLING |

| 상황 | HTTP | 예 |
| --- | --- | --- |
| 입력은 맞지만 규칙 위반 | 400 | 포인트 사용 한도 초과 |
| 자격 없음 | 403 | 멤버십 없이 자동 장바구니 |
| 대상 없음, 다른 회원 소유 | 404 | 없는 레시피 |
| 현재 상태와 맞지 않음, 중복 | 409 | 이미 확정된 자동 구성, 재고 부족 |

- 번호는 접두어 안에서 001부터 붙이고, 지운 번호는 다시 쓰지 않습니다.
- enum 상수 이름은 상황을 드러내게 씁니다(`RECIPE_NOT_FOUND`, `AUTO_CART_ALREADY_CONFIRMED`).
- message는 사용자에게 그대로 보여줄 수 있는 문장으로 씁니다.
