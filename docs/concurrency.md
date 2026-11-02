# 동시성 전략과 검증 결과

공통 잠금 원칙과 도메인별 검증 결과를 모으는 문서입니다. 현재는 채빈 담당의 회원·배송지·멤버십 지급,
인증 관련 원자적 처리와 비밀번호 변경·탈퇴·체험 차단, 해지 예약·결제 결과 반영 계약·체험 생성 기반을 작성했습니다.

| 항목 | 내용 |
| --- | --- |
| 작성 담당 | 회원 부분 채빈, 나머지 부분은 각 도메인 담당 |
| 문서 갱신·검증일 | 2026-11-02 |
| 코드 기준 | `dev`의 `02043c5`(PR #339 병합). 직전 전체 검증 소스 #339의 `a561664`와 현재 src·build.gradle 동일 |
| 환경 | Java 17, Spring Boot 3.5.16, 실제 MySQL 8.4·Redis 7.4 Testcontainers, MockMvc |
| 실행 방식 | ExecutorService·CountDownLatch로 별도 스레드 요청, 필요 시 별도 DB 연결의 잠금 대기 확인 |
| 상태 | 회원 부분 검증 완료·문서 리뷰 대기, 전체 도메인 결과 취합 대기 |

## 1. 공통 원칙

같은 자원의 검증과 변경을 하나의 트랜잭션에서 처리하고, 경합이 있는 기존 행은 비관적 락으로 보호합니다.
신규 행이 아직 없으면 공통으로 존재하는 자원 행을 잠그거나 UNIQUE 등 DB 제약으로 최종 방어합니다.
여러 행을 잠글 때는 정해진 순서를 지키고, 외부 API 응답을 기다리는 동안 DB 잠금을 유지하지 않습니다.

공통 잠금 순서는 [컨벤션](convention.md#트랜잭션동시성)을 따릅니다.

```text
회원 잠금이 있는 흐름:
MEMBER → PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고

회원 잠금이 없는 기존 결제 흐름:
PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고

멤버십 결제 연동 계약의 순서(실제 청구·환불 Facade는 후속 연결):
MEMBER → BILLING_KEY → MEMBERSHIP → 멤버십 결제 회차 → 시도 → 이용료 환불
```

이는 기존 행의 잠금 획득 순서입니다. 신규 주문·결제 INSERT 순서를 일률적으로 바꾸는 규칙은 아닙니다.
각 도메인의 실제 잠금 대상·정렬 기준·실패 처리는 아래 도메인별 결과로 확인합니다.

## 2. 회원 도메인: 무엇을 보호하는가

배송지 등록·선택·수정·삭제와 관리자 멤버십 지급은 같은 회원을 기준으로 충돌할 수 있습니다.
배송지 개수만 따로 읽으면 둘 다 4개로 판단한 뒤 6개가 될 수 있고,
선택과 삭제를 따로 검사하면 삭제된 주소를 현재 배송지로 지정하는 상태가 생길 수 있습니다.
활성 멤버십이 없는지 확인한 뒤 지급하는 요청도 동시에 들어오면 같은 문제를 겪습니다.

| 작업 | 유지해야 하는 조건 | 공유 잠금 대상 |
| --- | --- | --- |
| 배송지 등록 | 최대 5개, 첫 배송지 자동 선택 1건, 실패 시 선택·마트·동의 유지 | 회원 행 |
| 배송지 선택 | 현재 배송지와 이용 마트가 같은 선택 결과를 반영 | 회원 행 |
| 배송지 수정 | 현재 주소를 바꿀 때 이용 마트도 같은 트랜잭션에서 갱신 | 회원 행 |
| 배송지 삭제 | 현재 배송지 삭제 금지, 삭제된 배송지 선택 금지 | 회원 행 |
| 관리자 멤버십 지급 | 활성·결제 대기·전환 대기 이용권과 중복 지급 금지 | 회원 행 |
| 해지 예약 | 본인 이용권에 최초 예약 시각 유지, 유예 중에는 즉시 혜택 종료 | 회원 → 해당 이용권 PK |
| 체험 생성 기반 | 실제 이용 이력·번호 차단·중복 생성 검사 후 정확히 7일 체험 1건 생성 | 호출자가 회원 잠금 선행, 쓰기 트랜잭션 필수 |
| 비밀번호 변경·탈퇴 | 같은 현재 비밀번호 변경 및 동시 탈퇴 중복 처리 방지 | 회원 행 |
| 신규 주문과 탈퇴 상태 확인 | 잠금 대기 후 탈퇴 회원이면 주문·재고 변경 거절 | 회원 행 |

### 2-1. 후보와 선택 이유

| 후보 | 장점 | 이 요구사항에서의 한계 |
| --- | --- | --- |
| 조회 후 일반 저장 | 구현이 단순 | 검증과 저장 사이에 다른 요청이 들어올 수 있음 |
| 자바 synchronized | 같은 JVM 내부 요청을 직렬화 가능 | 여러 앱 인스턴스의 요청까지 보호하지 못함 |
| 낙관적 락 | 충돌이 적을 때 대기 없이 처리 가능 | 충돌 후 재시도·전체 조건 재검증이 필요하며 현재 팀 규칙에서는 사용하지 않음 |
| **회원 행 비관적 락(선택)** | 등록할 배송지·이용권이 없어도 공통 회원 행을 잠가 검증·변경을 직렬화 | 같은 회원의 작업이 기다리며 긴 트랜잭션·잠금 순서·타임아웃을 관리해야 함 |

배송지별 행만 잠그면 다른 주소의 선택·삭제나 새 주소 등록이 같은 기준으로 묶이지 않습니다.
회원 행은 이미 존재하고 모든 대상 작업이 공유하므로, 해당 행을 잠근 후 현재 조건을 확인합니다.
회원별로 다른 행을 사용하지만 여러 회원의 실제 부하·처리량 개선은 이번 검증에서 측정하지 않았습니다.

### 2-2. 코드에서의 적용

[MemberRepository.findByIdForUpdate](../src/main/java/com/recipick/domain/member/repository/MemberRepository.java)는
`PESSIMISTIC_WRITE`로 회원을 조회합니다.
[MemberService](../src/main/java/com/recipick/domain/member/service/MemberService.java)는 일반·잠금 조회에서 탈퇴 회원도 `MEMBER_NOT_FOUND`로 거절합니다.

[MemberAddressFacade](../src/main/java/com/recipick/domain/member/facade/MemberAddressFacade.java)의 등록·선택·수정·삭제는
쓰기 트랜잭션의 첫 DB 작업으로 회원을 잠급니다. 주소 개수·선택 상태·이용 마트·최초 동의 갱신은 그 뒤에 처리합니다.
[MembershipGrantFacade.grant](../src/main/java/com/recipick/domain/member/facade/MembershipGrantFacade.java)는
회원 잠금 후 관리자 권한과 활성·대기 이용권을 확인하고 지급 서비스를 호출합니다.
[MembershipGrantService](../src/main/java/com/recipick/domain/member/service/MembershipGrantService.java)는
`MANDATORY`와 읽기 전용 거절로 같은 바깥 트랜잭션에 합류하고 30일 이용권 생성·감사 기록을 처리합니다.
미해결 멤버십 결제 조회 C-17은 이 코드 기준 dev에 아직 없어 최종 지급 검사 연결은 남아 있습니다.

```text
쓰기 트랜잭션 시작
→ 회원 행 잠금
→ 잠금 후 현재 상태·소유권·개수·이용권 조건 확인
→ 업무 변경
→ 커밋 또는 롤백
→ 잠금 해제
```

같은 트랜잭션에서 잠금 전에 일반 조회로 스냅샷을 만들거나 회원 엔티티를 미리 로딩하지 않는 것이 규칙입니다.
잠금을 기다린 뒤의 검증이 이전 조회 값에 기대지 않도록 첫 DB 작업을 통일합니다.
회원 토큰 인증의 상태 검사는 별도 존재 조회이며 회원 엔티티를 선로딩하지 않습니다.

다른 도메인에서 회원 엔티티가 필요 없으면 `lockActiveMember(memberId)`를 사용할 수 있습니다.
이 메서드는 쓰기 가능한 바깥 트랜잭션이 필수이며 반환 후에도 바깥 트랜잭션 종료까지 잠금을 유지합니다.
현재 주문 생성은 기존 `getMemberForUpdate` 호출을 유지하며 같은 탈퇴 검사를 적용받습니다.

### 2-3. 잠금 실패와 롤백

테스트 MySQL은 `--innodb-lock-wait-timeout=5`로 시작하고, 로컬 Compose·예시 앱 연결에도 5초 설정을 둡니다.
운영 환경은 해당 DB·연결의 실제 설정을 확인해야 합니다. JPA 잠금 시간 힌트만으로 같은 동작을 가정하지 않습니다.

[GlobalExceptionHandler](../src/main/java/com/recipick/global/exception/GlobalExceptionHandler.java)는
비관적 락 획득 실패를 `409 COMMON-010`으로 변환합니다.
잠금을 얻지 못한 요청은 업무 변경 없이 종료하며, 이번 흐름에 무조건 자동 재시도를 추가하지 않습니다.
`COMMON-006`은 동일 요청 처리 중·멱등성 충돌의 별도 계약이며 잠금 대기 시간 초과와 구분합니다.

등록·선택·수정·삭제·지급 후 같은 트랜잭션에서 예외가 발생하면 DB 변경 전체를 롤백합니다.
현재 배송지·마트·동의 시각처럼 함께 바뀌는 값도 같은 경계에서 확인합니다.

## 3. 실제 동시 요청·실패 결과

회원 DB 상태 검증은 테스트 트랜잭션 하나로 감싸지 않고, 별도 요청·연결에서 커밋한 뒤 JDBC로 최종 값을 확인합니다.
잠금 대기 테스트는 별도 연결이 회원 행을 잡은 상태에서 실제 요청을 보내고,
주문 연동 경합은 다른 요청이 FOR UPDATE를 기다리는 것을 확인한 뒤 앞 트랜잭션을 종료합니다.

| 시나리오 | 동시 요청·방법 | 실제 결과 |
| --- | --- | --- |
| 배송지 4개에서 등록 | 6건 동시 요청 | 201 1건·400 ADDRESS-002 5건, 최종 5개 |
| 첫 배송지 등록 | 2건 동시 요청 | 둘 다 201, 자동 선택 응답 1건, 최종 배송지 2개·현재 배송지 1개 |
| 서로 다른 배송지 선택 | 2건 동시 요청 | 최종 현재 배송지와 이용 마트의 지역 일치 |
| 같은 배송지 선택·삭제 | 2건 동시 요청 | 삭제된 주소가 현재 배송지가 되지 않고 기존 참조 유효성 유지 |
| 관리자 멤버십 지급 | 같은 회원에게 6건 | 201 1건·409 MEMBERSHIP-002 5건, 이용권 1건 |
| 회원 잠금 시간 초과 | 등록·선택·수정·삭제·지급의 별도 요청 | 409 COMMON-010, 해당 변경·이용권 생성 없음 |
| 후속 처리 실패 | 업무 변경 후 같은 트랜잭션에서 예외 | 주소·선택·마트·동의 또는 이용권 롤백 |
| 탈퇴 상태 커밋과 주문 생성 | 회원 잠금 대기 확인 후 탈퇴 상태 반영 | MEMBER-001, 주문 생성 없음·재고 유지 |
| 같은 키 주문 생성 경합 | 앞 요청이 회원 잠금 후 주문 커밋 | 뒤 요청은 기존 주문 반환, 주문 1건·재고 1회 차감 |

수정 작업은 락 대기·롤백을 검증했습니다. 서로 다른 수정 요청 조합 전체를 실험한 것으로 확대하지 않습니다.
경합 결과는 최종 데이터 불변 조건을 기준으로 판정하며, 어느 요청이 먼저 성공할지는 고정하지 않습니다.

| 검증 클래스 | 테스트 | 실패 | 오류 | 건너뜀 |
| --- | --- | --- | --- | --- |
| [MemberAddressCreateConcurrencyTest](../src/test/java/com/recipick/domain/member/controller/MemberAddressCreateConcurrencyTest.java) | 4 | 0 | 0 | 0 |
| [MemberAddressSelectConcurrencyTest](../src/test/java/com/recipick/domain/member/controller/MemberAddressSelectConcurrencyTest.java) | 3 | 0 | 0 | 0 |
| [MemberAddressUpdateConcurrencyTest](../src/test/java/com/recipick/domain/member/controller/MemberAddressUpdateConcurrencyTest.java) | 2 | 0 | 0 | 0 |
| [MemberAddressDeleteConcurrencyTest](../src/test/java/com/recipick/domain/member/controller/MemberAddressDeleteConcurrencyTest.java) | 3 | 0 | 0 | 0 |
| [MembershipGrantConcurrencyTest](../src/test/java/com/recipick/domain/member/controller/MembershipGrantConcurrencyTest.java) | 3 | 0 | 0 | 0 |
| [OrderCreateMemberLockTest](../src/test/java/com/recipick/domain/order/facade/OrderCreateMemberLockTest.java) | 3 | 0 | 0 | 0 |
| 합계 | 18 | 0 | 0 | 0 |

### 3-1. 인증에 사용하는 Redis 원자적 처리

로그인 실패 횟수와 리프레시 교체는 DB 행 잠금 대신 Redis Lua 조건부 갱신으로 처리합니다.
이는 회원 행 잠금과 다른 저장 자원의 경합을 해결하는 방식입니다.

| 경합 | 실제 결과 | 근거 |
| --- | --- | --- |
| 로그인 실패 6건 | 회원·관리자 각각 최초 401 2건, 나머지 잠금 거절, 실패 횟수 3 유지 | MemberLoginIntegrationTest·AdminLoginIntegrationTest |
| 같은 리프레시 토큰으로 재발급 6건 | 1건만 성공, 나머지 AUTH-011 | TokenManagementIntegrationTest |
| 재발급과 로그아웃 | 로그아웃 후 Redis 세션 없음, 새 토큰도 재사용 거절 | TokenManagementIntegrationTest |

정책·실패 응답과 클래스별 실행 결과는 [인증 문서](adr/authentication.md)에 있습니다.

### 3-2. 비밀번호 변경·탈퇴·체험 차단 검증 범위

[MemberWithdrawalFoundationIntegrationTest](../src/test/java/com/recipick/domain/member/service/MemberWithdrawalFoundationIntegrationTest.java)는
DB에 탈퇴 상태를 저장한 뒤 기존 액세스 토큰·리프레시 세션·정상 비밀번호·회원 조회·잠금·지급 진입점의 거절을 확인합니다.
이 테스트는 6개이며 이번 실행에서 모두 통과했습니다. 인증 문서에도 같은 클래스가 포함돼 있어 중복 합산하지 않습니다.

탈퇴 API는 구현됐습니다. `MemberWithdrawalProcessor`는 첫 DB 작업으로 회원을 잠근 뒤 비밀번호·진행 중 주문·활성 또는 대기 이용권과
정산 Checker의 전체 Scope 연결 여부를 확인합니다. Processor의 주문·멤버십 검사는 일반 조회이며,
상품 결제 Checker 내부는 별도의 최신 커밋 잠금 읽기를 사용합니다. 모든 상태 검사가 무잠금이라는 뜻은 아닙니다.
주문 생성도 회원 잠금을 `findByIdempotencyKey`보다 먼저 획득하므로 회원 단위로 검증·생성을 직렬화합니다.
이 보장은 탈퇴 제한 상태를 새로 만드는 경로가 같은 회원 잠금 규칙에 참여한다는 전제이며, 향후 구독·자동청구 계약도 확인해야 합니다.

| 검증 | 확인한 결과 | 근거 |
| --- | --- | --- |
| 같은 현재 비밀번호로 동시 변경 | 1건만 성공, 이후 요청은 현재 비밀번호 불일치로 거절 | MemberPasswordChangeIntegrationTest |
| 비밀번호 변경 롤백·늦은 이전 세션 저장 | 비밀번호와 버전을 함께 롤백, 늦게 저장된 이전 버전 세션은 재발급 거절 | MemberPasswordChangeIntegrationTest |
| 동시 탈퇴 2건 | 테스트용 전체 정산 계약 아래 1건 성공, 나머지 MEMBER-001 | MemberWithdrawalIntegrationTest |
| 탈퇴 후속 작업 실패 | 탈퇴 상태와 체험 차단 기록 모두 롤백 | MemberWithdrawalIntegrationTest |
| Scope 누락·정산 검사 실패 | 개인정보 변경·체험 차단 기록을 시작하지 않음 | MemberWithdrawalProcessorTest·MemberWithdrawalIntegrationTest |
| 같은 휴대전화 해시 동시 저장 | UNIQUE와 조건부 upsert로 1행 및 가장 늦은 만료 시각 유지 | TrialBlockIntegrationTest |

비밀번호 변경·탈퇴는 DB 트랜잭션 전에 삭제할 Redis 세션 키를 확보하고 커밋 후 해당 키만 삭제합니다.
Redis 삭제의 원자성과 DB 커밋의 원자성을 동일하게 보지 않으며, DB의 버전·탈퇴 상태로 접근을 막습니다.
`TRIAL_BLOCK`은 같은 번호로 재가입한 회원과 이전 회원이 서로 다른 회원 행을 사용하므로 해시 UNIQUE와
만료 시각을 줄이지 않는 upsert로 추가 보호합니다. 만료 정리는 조건부 삭제로 유효 기록을 유지합니다.

**현재 실제 Checker는 `PRODUCT_PAYMENT`·`POINTS`가 연결됐고 `MEMBERSHIP_PAYMENT`·`AUTO_BILLING`은 미연결이라
탈퇴 성공은 차단됩니다.** 회원 도메인의 기존 탈퇴 성공·경합 테스트는 테스트용 전체 Scope 계약을 사용합니다.
결제 담당의 `WithdrawalSettlementApiIntegrationTest`는 실제 상품 결제·포인트 검사와 미연결 두 Scope의 테스트용 스텁을 사용합니다.
이를 운영에서 네 Scope 전체를 연결한 결과로 해석하지 않습니다.

[ProductPaymentSettlementChecker](../src/main/java/com/recipick/domain/payment/facade/ProductPaymentSettlementChecker.java)는
주문 서비스에서 전체 주문 ID를 받아 상품 결제의 미해결 결제·환불·웹훅을 검사합니다.
회원 잠금만으로 기존 상품 결제 후속 처리와 직렬화할 수 없어 판정 행을 잠금 읽기로 확인합니다.
[ProductPaymentSettlementConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/ProductPaymentSettlementConcurrencyTest.java)는
일반 읽기 스냅샷 뒤 커밋된 환불·웹훅을 놓치지 않는 것과 이미 접수된 거래의 후속 처리가 잠금 해제 뒤 계속되는 것을 검증합니다.
포인트 검사는 마지막에 실행하며 미정산 차단, 확인 잔액 불일치, 소멸·원장 정합성 실패 시 탈퇴 변경도 함께 롤백합니다.

멤버십 결제·자동결제 Checker, 장바구니·찜 정리와 최종 양방향 경합은 후속 연결·검증 대상입니다.
기존 상품 결제의 승인·실패·만료·환불·웹훅 경로에 회원 잠금·탈퇴 검사를 일괄 추가하지 않습니다.
멤버십 신규 결제 경로의 잠금 참여와 기접수 거래 후속 처리의 탈퇴 후 진행은 결제 담당의 실제 연동에서 별도로 검증합니다.

### 3-3. 해지 예약·이용권 생명주기·결제 반영·체험 생성

[MembershipCancelReservationService](../src/main/java/com/recipick/domain/member/service/MembershipCancelReservationService.java)는
회원 행 다음에 해당 회원의 이용권 PK를 잠급니다. 회원 ID 범위를 `FOR UPDATE`로 조회하지 않습니다.
해지 예약은 결제·전환 대기에도 가능하며, 유예 중 예약은 `grace_end_at`을 지워 현재 혜택을 즉시 종료합니다.

[MembershipBillingService](../src/main/java/com/recipick/domain/member/service/MembershipBillingService.java)의
잠금·생성·결과 반영은 쓰기 가능한 바깥 트랜잭션이 필수입니다. 호출자는 소유권·현재 회차·미반영 결과인지 검증한 뒤 반영하고,
중복은 `ALREADY_APPLIED`, 적용할 수 없는 상태는 `CONFLICT`로 구분합니다. 예외는 전파해 호출자의 선행 DB 변경도 롤백합니다.
`MANDATORY` 자체가 회원·이용권 잠금 획득이나 회차 검증을 대신 보장하지는 않습니다.

`findMemberIdOfMembership`은 잠금 트랜잭션에 들어가기 전 별도 읽기로 소유 회원 ID만 찾습니다.
이후 쓰기 트랜잭션의 첫 DB 작업은 회원 잠금이며, 이용권 PK 잠금과 소유권 재검증을 다시 수행합니다.
매핑 조회를 같은 쓰기 트랜잭션의 회원 잠금 앞에 넣어 스냅샷을 만드는 흐름으로 사용하지 않습니다.

| 검증 | 확인한 결과 | 범위·근거 |
| --- | --- | --- |
| 같은 이용권 해지 예약 동시 요청 | 최초 예약 시각 유지, 활성 기간 혜택 유지 | MembershipCancelReservationIntegrationTest의 실제 요청·별도 연결 |
| 결제·전환 대기와 유예 중 예약 | 결제 대기는 PAYMENT_PENDING 유지, 전환·유예 종료는 EXPIRED | 같은 API 통합 테스트 |
| 활성화 전·유예 종료 경계 | 미활성은 혜택 제외, 유예 끝 미포함, 회원 조회·관리자 검색·getTypeAt 일치 | MembershipLifecycleIntegrationTest |
| 첫 결제·갱신 중복·충돌 | 최초 활성화 시각 유지, 기간 단축 없음, 해지 예약·자동갱신 중단 유지 | MembershipBillingTest·MembershipBillingIntegrationTest |
| 자동갱신 성공에 유예 종료 누락 | INVALID_INPUT, 기존 시작·활성화·종료 시각 유지 | MembershipBillingTest |
| 결제 결과 반영 실패 | 호출자의 선행 DB 변경과 이용권 생성까지 롤백 | MembershipBillingIntegrationTest. 실제 PG 결과 저장 전체 흐름의 검증은 아님 |
| 체험 생성 동시 요청 | 회원 잠금을 먼저 획득하는 별도 트랜잭션 2건 중 1건 생성, 나머지 MEMBERSHIP-002 | MembershipTrialIntegrationTest. 카드·미해결 결제 검사는 아직 연결하지 않은 내부 생성 기반 검증 |
| 체험 자격·전화번호 차단 | 활성화 이력 기준 거절, 실패한 미활성 결제는 체험 가능, 번호 차단은 만료 시각부터 제외 | MembershipTrialIntegrationTest |

| 추가 검증 클래스 | 테스트 | 실패 | 오류 | 건너뜀 |
| --- | --- | --- | --- | --- |
| [MembershipCancelReservationIntegrationTest](../src/test/java/com/recipick/domain/member/controller/MembershipCancelReservationIntegrationTest.java) | 19 | 0 | 0 | 0 |
| [MembershipLifecycleIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipLifecycleIntegrationTest.java) | 5 | 0 | 0 | 0 |
| [MembershipBillingTest](../src/test/java/com/recipick/domain/member/entity/MembershipBillingTest.java) | 13 | 0 | 0 | 0 |
| [MembershipBillingIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipBillingIntegrationTest.java) | 14 | 0 | 0 | 0 |
| [MembershipGrantFacadeTest](../src/test/java/com/recipick/domain/member/facade/MembershipGrantFacadeTest.java) | 3 | 0 | 0 | 0 |
| [MembershipGrantTransactionIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipGrantTransactionIntegrationTest.java) | 2 | 0 | 0 | 0 |
| [MembershipTrialIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipTrialIntegrationTest.java) | 18 | 0 | 0 | 0 |
| 추가 합계 | 74 | 0 | 0 | 0 |

현재 `getTypeAt`은 저장된 이용 기간·활성화·유예 필드로 판정하며 지워진 과거 유예 이력을 완전히 재현하지 않습니다.
주문 당시 적립률은 주문에 저장해 이후 멤버십 변경으로 재계산하지 않습니다. 조회 계약의 이 한계는 실제 청구·환불 이력 전체 보존과 구분합니다.
체험 생성은 아직 공개 Controller가 없고, 구독·정기결제·즉시 해지와의 동시 경합도 후속 연동 검증 범위입니다.

## 4. 재현 환경과 명령

[IntegrationTestSupport](../src/test/java/com/recipick/IntegrationTestSupport.java)는 실제 MySQL 8.4·Redis 7.4를 Testcontainers로 실행합니다.
공통 테스트 계정·시드는 테스트 환경에서만 사용하며 HTTP는 MockMvc로 호출합니다.
JUnit·Mockito 기반 토큰 단위 테스트와 실제 DB·Redis 통합 테스트를 구분합니다.

아래 명령은 직전 전체 테스트 2,736건(실패 0·오류 0·AI 평가 건너뜀 2) 리포트에서 추린 회원 관련 25개 클래스를 재현합니다.
문서 갱신을 위해 재실행하지 않았습니다. 인증 문서와 겹치는 클래스를 중복 합산하지 않습니다.

```bash
./gradlew test \
  --tests 'com.recipick.global.security.JwtTokenProviderTest' \
  --tests 'com.recipick.global.security.AuthenticationWebTest' \
  --tests 'com.recipick.domain.member.service.MemberLoginIntegrationTest' \
  --tests 'com.recipick.domain.member.service.AdminLoginIntegrationTest' \
  --tests 'com.recipick.domain.member.service.TokenManagementIntegrationTest' \
  --tests 'com.recipick.domain.member.service.MemberWithdrawalFoundationIntegrationTest' \
  --tests 'com.recipick.domain.member.controller.MemberPasswordChangeIntegrationTest' \
  --tests 'com.recipick.domain.member.facade.MemberPasswordFacadeTest' \
  --tests 'com.recipick.domain.member.controller.MemberWithdrawalIntegrationTest' \
  --tests 'com.recipick.domain.member.facade.MemberWithdrawalProcessorTest' \
  --tests 'com.recipick.domain.member.facade.MemberWithdrawalFacadeTest' \
  --tests 'com.recipick.domain.member.service.TrialBlockIntegrationTest' \
  --tests 'com.recipick.domain.member.controller.MemberAddressCreateConcurrencyTest' \
  --tests 'com.recipick.domain.member.controller.MemberAddressSelectConcurrencyTest' \
  --tests 'com.recipick.domain.member.controller.MemberAddressUpdateConcurrencyTest' \
  --tests 'com.recipick.domain.member.controller.MemberAddressDeleteConcurrencyTest' \
  --tests 'com.recipick.domain.member.controller.MembershipGrantConcurrencyTest' \
  --tests 'com.recipick.domain.order.facade.OrderCreateMemberLockTest' \
  --tests 'com.recipick.domain.member.controller.MembershipCancelReservationIntegrationTest' \
  --tests 'com.recipick.domain.member.service.MembershipLifecycleIntegrationTest' \
  --tests 'com.recipick.domain.member.entity.MembershipBillingTest' \
  --tests 'com.recipick.domain.member.service.MembershipBillingIntegrationTest' \
  --tests 'com.recipick.domain.member.facade.MembershipGrantFacadeTest' \
  --tests 'com.recipick.domain.member.service.MembershipGrantTransactionIntegrationTest' \
  --tests 'com.recipick.domain.member.service.MembershipTrialIntegrationTest'
```

리포트 집계: **25개 클래스·266개 테스트, 실패 0·오류 0·건너뜀 0**.
인증 문서의 174개, 기존 경합 6개 클래스의 18개, 추가 멤버십 7개 클래스의 74개를 중복 없이 합친 결과입니다.
이는 전부 동시 요청 테스트라는 의미가 아니며 단위·API·상태 경계·롤백 검증도 포함합니다.
원본 전체 실행은 `./gradlew test`의 **2,736건·실패 0·오류 0·건너뜀 2·BUILD SUCCESSFUL**입니다.
이번 리포트에는 UTC JVM 강제를 추가하지 않았습니다. UTC 환경 점검을 추가하려면
`JAVA_TOOL_OPTIONS=-Duser.timezone=UTC ./gradlew --no-daemon test`를 사용하며 운영 한국 시간 정책과 구분합니다.

원본 리포트는 `build/reports/tests/test/index.html`, 클래스별 XML은 `build/test-results/test/`에 생성됩니다.
배포 환경의 여러 앱 인스턴스·부하·실 PG 호출 테스트를 수행한 결과는 아닙니다.
기존 테스트 종료 시 공유 스키마 중복 정리 로그와 실제 테스트 실패 여부는 구분해서 확인합니다.

## 5. 다른 도메인의 작성 위치

| 영역 | 담당 | 추가할 내용·현재 문서 상태 |
| --- | --- | --- |
| 상품·재고 | 태헌 | 작성 대기: 재고 차감·관리자 조정·복구의 잠금 대상·순서·동시 요청 결과 |
| 레시피 자동 구성 | 보민 | 작성 대기: 확정 중복 방어·장바구니 합산 경합·실패 롤백 결과 |
| 주문 | 준희 | 작성 대기: 주문 생성·멱등 키·결제 대기 제한·취소와 수락 경합. 위 회원 잠금 연동은 검증된 일부 사례 |
| 결제·환불·포인트 | 정윤 | 작성 대기: 승인·만료·취소·웹훅·포인트의 경합·멱등·재처리 결과 |

각 담당자는 보호할 조건 → 후보·선택 이유 → 실제 잠금·제약 → 실패 처리 → 재현 명령·수치·테스트 근거 순으로 추가합니다.
`작성 대기`는 문서 취합 상태이며 해당 기능 코드의 미구현 여부를 뜻하지 않습니다.

## 6. 관련 규칙

- [요구사항](requirement.md), [트랜잭션·동시성 컨벤션](convention.md#트랜잭션동시성).
- [회원 Repository](../src/main/java/com/recipick/domain/member/repository/MemberRepository.java), [배송지 Facade](../src/main/java/com/recipick/domain/member/facade/MemberAddressFacade.java), [멤버십 지급 Facade](../src/main/java/com/recipick/domain/member/facade/MembershipGrantFacade.java).
- [주문 회원 잠금 테스트](../src/test/java/com/recipick/domain/order/facade/OrderCreateMemberLockTest.java), [회원 탈퇴 기반 테스트](../src/test/java/com/recipick/domain/member/service/MemberWithdrawalFoundationIntegrationTest.java).
- [인증 근거](adr/authentication.md), [아키텍처](adr/layered-architecture.md), [문서 목차](README.md).
