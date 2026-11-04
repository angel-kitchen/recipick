# 동시성 전략과 검증 결과

공통 잠금 원칙과 도메인별 검증 결과를 모으는 문서입니다. 현재는 채빈 담당의 회원·배송지·멤버십 지급,
인증 관련 원자적 처리와 비밀번호 변경·탈퇴·체험 차단, 해지 예약·결제 결과 반영 계약·체험 생성 기반을 작성했습니다.
레시피 도메인(보민)의 자동 구성 확정·찜·AI 요청 한도는 5절에 있습니다.

| 항목 | 내용 |
| --- | --- |
| 작성 담당 | 회원 부분 채빈, 나머지 부분은 각 도메인 담당 |
| 문서 갱신·검증일 | 2026-11-02 |
| 코드 기준 | `dev`의 `84c4c0b`(구독 #382·결제 정합화 #380 병합). 실행 근거는 #382의 `434ea0f` XML이며 최신 dev 재실행 결과와 구분 |
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

멤버십 결제 흐름의 공통 잠금 순서(작업별 필요한 행만 잠금):
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
| 체험 시작 API | 실제 이용 이력·번호 차단·카드·미정산 검사 후 7일 체험 1건 생성 | Facade 첫 DB 작업으로 회원 잠금 |
| 유료 구독 API | 같은 멱등 키의 이용권·회차·시도 중복 생성 방지 | 회원 Facade는 NEVER, 결제 TX1의 첫 회원 잠금 뒤 멱등 키 조회 |
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
관리자 지급의 활성·결제 대기·전환 대기 이용권 차단은 `existsBlockingMembership`로 이미 연결돼 있습니다.
C-17도 연결돼 회원 잠금 뒤 종료·만료를 포함한 전체 이용권 ID의 미해결 결제를 확인합니다. 미정산이면 `MEMBERSHIP-009`로 생성·감사 기록 전에 거절합니다. 실제 청구와 지급의 전체 양방향 경합은 추가 검증 범위입니다.

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

**현재 실제 Checker는 `PRODUCT_PAYMENT`·`POINTS`·`MEMBERSHIP_PAYMENT`가 연결됐고 `AUTO_BILLING`은 미연결이라
탈퇴 성공은 차단됩니다.** 회원 도메인의 기존 탈퇴 성공·경합 테스트는 테스트용 전체 Scope 계약을 사용합니다.
#382 실행 시점의 결제 담당 `WithdrawalSettlementApiIntegrationTest`는 실제 상품 결제·포인트 검사와 나머지 Scope의 테스트용 스텁을 사용했습니다. 이후 #380의 멤버십 정산 Checker 병합 상태와 해당 과거 실행 범위를 구분합니다.
이를 운영에서 네 Scope 전체를 연결한 결과로 해석하지 않습니다.

[ProductPaymentSettlementChecker](../src/main/java/com/recipick/domain/payment/facade/ProductPaymentSettlementChecker.java)는
주문 서비스에서 전체 주문 ID를 받아 상품 결제의 미해결 결제·환불·웹훅을 검사합니다.
회원 잠금만으로 기존 상품 결제 후속 처리와 직렬화할 수 없어 판정 행을 잠금 읽기로 확인합니다.
[ProductPaymentSettlementConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/ProductPaymentSettlementConcurrencyTest.java)는
일반 읽기 스냅샷 뒤 커밋된 환불·웹훅을 놓치지 않는 것과 이미 접수된 거래의 후속 처리가 잠금 해제 뒤 계속되는 것을 검증합니다.
포인트 검사는 마지막에 실행하며 미정산 차단, 확인 잔액 불일치, 소멸·원장 정합성 실패 시 탈퇴 변경도 함께 롤백합니다.

[MembershipPaymentSettlementChecker](../src/main/java/com/recipick/domain/payment/facade/MembershipPaymentSettlementChecker.java)는 회원 잠금 뒤 전체 이용권 ID로 C-17을 호출합니다.
기존 정산은 탈퇴 회원도 잠글 수 있는 `lockMemberForSettlement`로 처리하고, 신규 접수용 `lockActiveMember`의 탈퇴 거절은 유지합니다.
장바구니 삭제는 모든 정산 통과 뒤 `trialBlocks.recordWithdrawal` 전에 같은 트랜잭션으로 연결돼 있으며 담기 이력은 유지합니다.
자동결제 Checker·찜 정리와 최종 양방향 경합은 후속 연결·검증 대상입니다.
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
| 체험 생성 동시 요청 | 회원 잠금을 먼저 획득하는 별도 트랜잭션 2건 중 1건 생성, 나머지 MEMBERSHIP-002 | MembershipTrialIntegrationTest(내부 생성)·MembershipTrialStartIntegrationTest(실제 카드·C-17을 거치는 동시 API 요청) |
| 체험 자격·전화번호 차단 | 활성화 이력 기준 거절, 실패한 미활성 결제는 체험 가능, 번호 차단은 만료 시각부터 제외 | MembershipTrialIntegrationTest |

| 추가 검증 클래스 | 테스트 | 실패 | 오류 | 건너뜀 |
| --- | --- | --- | --- | --- |
| [MembershipCancelReservationIntegrationTest](../src/test/java/com/recipick/domain/member/controller/MembershipCancelReservationIntegrationTest.java) | 19 | 0 | 0 | 0 |
| [MembershipLifecycleIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipLifecycleIntegrationTest.java) | 5 | 0 | 0 | 0 |
| [MembershipBillingTest](../src/test/java/com/recipick/domain/member/entity/MembershipBillingTest.java) | 13 | 0 | 0 | 0 |
| [MembershipBillingIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipBillingIntegrationTest.java) | 14 | 0 | 0 | 0 |
| [MembershipGrantFacadeTest](../src/test/java/com/recipick/domain/member/facade/MembershipGrantFacadeTest.java) | 5 | 0 | 0 | 0 |
| [MembershipGrantTransactionIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipGrantTransactionIntegrationTest.java) | 2 | 0 | 0 | 0 |
| [MembershipTrialIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipTrialIntegrationTest.java) | 21 | 0 | 0 | 0 |
| 추가 합계 | 79 | 0 | 0 | 0 |

현재 `getTypeAt`은 저장된 이용 기간·활성화·유예 필드로 판정하며 지워진 과거 유예 이력을 완전히 재현하지 않습니다.
주문 당시 적립률은 주문에 저장해 이후 멤버십 변경으로 재계산하지 않습니다. 조회 계약의 이 한계는 실제 청구·환불 이력 전체 보존과 구분합니다.
체험 시작 API와 구독 API가 공개돼 있습니다. 체험은 회원 잠금 아래 카드·C-17 검사와 생성을 처리합니다.
구독은 회원 Facade가 트랜잭션을 열지 않고 C-11을 호출하며, 결제 TX1에서 회원 잠금 → 멱등 키 조회 → 신규 접수 검사 → 접수 커밋 순서입니다.
같은 키 재요청을 회원 쪽 활성 이용권 검사로 먼저 막지 않습니다. PG는 접수 커밋 뒤 호출하고 결과 반영은 별도 트랜잭션입니다.
`MembershipSubscribeIntegrationTest`는 실제 접수·멱등 검사와 네 상태 응답을 확인하며 PG 실행 경계만 대체합니다.
구독·정기결제·즉시 해지와의 전체 양방향 경합은 해당 결제 테스트 및 최종 연동 검증과 구분합니다.


### 3-4. 추가 연결·정산 계약 검증

아래는 #382 전체 실행의 추가 검증입니다. #380에서 새로 추가한 결제 정합화 테스트 결과를 포함하지 않습니다.

| 검증 클래스 | 테스트 | 실패 | 오류 | 건너뜀 |
| --- | --- | --- | --- | --- |
| [MembershipGrantIntegrationTest](../src/test/java/com/recipick/domain/member/controller/MembershipGrantIntegrationTest.java) | 18 | 0 | 0 | 0 |
| [MembershipTrialStartIntegrationTest](../src/test/java/com/recipick/domain/member/controller/MembershipTrialStartIntegrationTest.java) | 23 | 0 | 0 | 0 |
| [MembershipTrialFacadeTest](../src/test/java/com/recipick/domain/member/facade/MembershipTrialFacadeTest.java) | 2 | 0 | 0 | 0 |
| [MembershipSubscribeIntegrationTest](../src/test/java/com/recipick/domain/member/controller/MembershipSubscribeIntegrationTest.java) | 16 | 0 | 0 | 0 |
| [MemberSettlementLockIntegrationTest](../src/test/java/com/recipick/domain/member/service/MemberSettlementLockIntegrationTest.java) | 6 | 0 | 0 | 0 |
| [MembershipBillingConflictIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipBillingConflictIntegrationTest.java) | 17 | 0 | 0 | 0 |
| [MembershipOverdueRenewalIntegrationTest](../src/test/java/com/recipick/domain/member/service/MembershipOverdueRenewalIntegrationTest.java) | 10 | 0 | 0 | 0 |
| 추가 연결 합계 | 92 | 0 | 0 | 0 |

회원 정산 잠금은 탈퇴 회원 허용·실제 PK 잠금 유지·바깥 쓰기 트랜잭션 요구를 확인합니다. 충돌 조회는 대상 제외·활성/대기·후속 생성 이력 및 종료 이력 제외를, 마감 누락 조회는 source·예약·유예 경계와 keyset을 확인합니다.

## 4. 재현 환경과 명령

[IntegrationTestSupport](../src/test/java/com/recipick/IntegrationTestSupport.java)는 실제 MySQL 8.4·Redis 7.4를 Testcontainers로 실행합니다.
공통 테스트 계정·시드는 테스트 환경에서만 사용하며 HTTP는 MockMvc로 호출합니다.
JUnit·Mockito 기반 토큰 단위 테스트와 실제 DB·Redis 통합 테스트를 구분합니다.

아래 명령은 #382 전체 실행 리포트에서 추린 회원 관련 32개 클래스를 재현합니다.
전체 테스트 근거는 PR #382의 `434ea0f`에서 실행한 3,091건(실패 0·오류 0·실제 AI 평가 건너뜀 2) XML입니다. 현재 `84c4c0b`에는 이후 병합된 결제 정합화 #380이 포함됩니다. 이번 문서 작업에서는 테스트를 재실행하지 않았으며, 3,091건을 최신 dev 전체 실행 결과로 해석하지 않습니다.
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
  --tests 'com.recipick.domain.member.service.MembershipTrialIntegrationTest' \
  --tests 'com.recipick.domain.member.controller.MembershipGrantIntegrationTest' \
  --tests 'com.recipick.domain.member.controller.MembershipTrialStartIntegrationTest' \
  --tests 'com.recipick.domain.member.facade.MembershipTrialFacadeTest' \
  --tests 'com.recipick.domain.member.controller.MembershipSubscribeIntegrationTest' \
  --tests 'com.recipick.domain.member.service.MemberSettlementLockIntegrationTest' \
  --tests 'com.recipick.domain.member.service.MembershipBillingConflictIntegrationTest' \
  --tests 'com.recipick.domain.member.service.MembershipOverdueRenewalIntegrationTest'
```

리포트 집계: **32개 클래스·366개 테스트, 실패 0·오류 0·건너뜀 0**.
인증 문서의 177개, 기존 경합 6개 클래스의 18개, 기존 추가 멤버십 7개 클래스의 79개, 추가 연결 7개 클래스의 92개를 중복 없이 합친 결과입니다.
이는 전부 동시 요청 테스트라는 의미가 아니며 단위·API·상태 경계·롤백 검증도 포함합니다.
원본 전체 실행은 `./gradlew test`의 **3,091건·실패 0·오류 0·건너뜀 2·BUILD SUCCESSFUL**(#382 실행 기준)입니다.
이번 리포트에는 UTC JVM 강제를 추가하지 않았습니다. UTC 환경 점검을 추가하려면
`JAVA_TOOL_OPTIONS=-Duser.timezone=UTC ./gradlew --no-daemon test`를 사용하며 운영 한국 시간 정책과 구분합니다.

원본 리포트는 `build/reports/tests/test/index.html`, 클래스별 XML은 `build/test-results/test/`에 생성됩니다.
배포 환경의 여러 앱 인스턴스·부하·실 PG 호출 테스트를 수행한 결과는 아닙니다.
기존 테스트 종료 시 공유 스키마 중복 정리 로그와 실제 테스트 실패 여부는 구분해서 확인합니다.

## 5. 레시피 도메인: 자동 구성 확정·찜·AI 요청 한도

| 항목 | 내용 |
| --- | --- |
| 작성 담당 | 보민 |
| 코드 기준 | `dev`의 `8215c63` |
| 실행 근거 | 2026-11-03 같은 커밋의 `./gradlew test` 전체 실행 3,384건(실패 0·오류 0·실제 AI 평가 건너뜀 2) XML |

### 5-1. 무엇을 보호하는가

| 작업 | 유지해야 하는 조건 | 방어 수단 |
| --- | --- | --- |
| 자동 구성 확정 | 한 요청은 장바구니에 한 번만 합산. 확정과 탈퇴가 겹쳐도 탈퇴 뒤 장바구니 항목이 남지 않음 | 회원 행 → 요청 행 비관적 락 + `PREVIEWED` 상태 검사 |
| 확정 중 장바구니 합산 | 같은 상품 수량 증가가 유실되지 않고 최대 수량·재고를 넘지 않음 | 주문 도메인 `CartService`의 조건부 UPDATE + UNIQUE(`member_id`, `product_id`) |
| 레시피 찜 | 같은 회원·레시피 찜은 1건 | 복합 PK(`member_id`, `recipe_id`) |
| AI 요청 한도 | 회원별 1분 5회를 넘지 않음 | Redis Lua 스크립트로 증가와 만료 설정을 한 번에 처리 |

### 5-2. 확정의 후보와 선택 이유

| 후보 | 장점 | 이 요구사항에서의 한계 |
| --- | --- | --- |
| 상태 조회 후 일반 저장 | 구현이 단순 | 두 요청이 모두 `PREVIEWED`를 읽고 둘 다 장바구니에 합산 |
| 조건부 UPDATE(`WHERE status = 'PREVIEWED'`) | 잠금 대기 없이 상태 전환이 원자적 | 상태 규칙이 엔티티 메서드가 아닌 쿼리에 놓임. 컨벤션은 겹치는 자원을 잠근 뒤 검증·변경하도록 정함 |
| 낙관적 락 | 충돌이 적으면 대기 없음 | 컨벤션상 금지, 충돌 후 재시도 필요 |
| **요청 행 비관적 락 + 상태 검사(선택)** | 컨벤션의 기본 방식이고 상태 규칙을 `AutoCartRequest.validateConfirmable()`에 둠. 뒤 요청은 기다린 뒤 `RECIPE-015`로 명확히 거절 | 같은 요청의 확정끼리는 앞 트랜잭션이 끝날 때까지 대기 |

요구사항의 확정 문구도 이 방식에 맞춰 고쳤습니다.
회원 행을 먼저 잠그는 것은 탈퇴와의 경합 때문이며, 공통 잠금 순서의 "회원 포함 시 `MEMBER`가 맨 앞"을 따릅니다(#334).

### 5-3. 코드에서의 적용

```text
AutoCartFacade.confirm (@Transactional)
→ MemberService.lockActiveMember: 회원 행 잠금, 탈퇴 회원이면 MEMBER-001
→ AutoCartRequestRepository.findByIdAndMemberIdForUpdate: 요청 행 PESSIMISTIC_WRITE
→ validateConfirmable(): PREVIEWED가 아니면 RECIPE-015(409)
→ 멤버십·이용 마트 변경·이전 마트 상품 검사
→ 가격·재고·수량 재계산 → CartService.addAutoCartItem으로 장바구니 합산
→ CONFIRMED와 확정 시점 결과 저장 → 커밋
```

- **장바구니 합산:** 기존 항목은 조건부 UPDATE로 수량을 늘리고, 바뀐 행이 0이면 `CART-007`입니다. 새 항목 INSERT가 UNIQUE에 걸려도 `CART-007`입니다. 확정 중 이 오류가 나면 확정 트랜잭션 전체가 롤백되어 요청은 `PREVIEWED`로 남고 다시 확정할 수 있습니다.
- **찜:** `existsById`로 먼저 막고, 동시에 통과한 요청은 `saveAndFlush`의 PK 중복(`DataIntegrityViolationException`)을 `RECIPE-017`(409)로 바꿉니다. `Persistable.isNew()`로 조회 없이 INSERT하도록 했습니다.
- **AI 요청 한도:** `RedisAiRequestCounter`가 `INCR`와 첫 요청의 `PEXPIRE`를 Lua 스크립트 하나로 실행합니다. 둘을 따로 호출하면 그 사이 실패 시 만료 없는 키가 남아 회원이 계속 막힐 수 있습니다. 서버가 여러 대여도 같은 Redis 키를 셉니다.

### 5-4. 잠금 실패와 롤백

- 잠금 대기 시간 초과는 회원 도메인과 같이 `409 COMMON-010`이며 자동 재시도하지 않습니다.
- 회원 잠금에서 탈퇴로 거절되면 장바구니 변경 없이 롤백되고 요청은 `PREVIEWED`로 남습니다.
- 확정은 한 트랜잭션이라 장바구니 일부만 담기고 끝나는 경우가 없습니다.

### 5-5. 실제 동시 요청·실패 결과

| 시나리오 | 동시 요청·방법 | 실제 결과 |
| --- | --- | --- |
| 같은 요청 동시 확정 | 5건 동시 요청 | 성공 1건·`RECIPE-015` 4건, 장바구니 수량 1회 합산(2) |
| 확정 중 탈퇴 | 회원 잠금이 `MEMBER-001`을 던지도록 고정 | 장바구니 항목 없음, 요청 `PREVIEWED` 유지 |
| 같은 레시피 동시 찜 | 10건 동시 요청 | 성공 1건·`RECIPE-017` 9건, 찜 1건 |
| AI 요청 동시 | 같은 회원 10건 동시 요청 | 통과 5건·`AI-001` 5건 |

| 검증 클래스 | 테스트 | 실패 | 오류 | 건너뜀 |
| --- | --- | --- | --- | --- |
| [AutoCartFacadeIntegrationTest](../src/test/java/com/recipick/domain/recipe/facade/AutoCartFacadeIntegrationTest.java) | 15 | 0 | 0 | 0 |
| [RecipeBookmarkConcurrencyTest](../src/test/java/com/recipick/domain/recipe/service/RecipeBookmarkConcurrencyTest.java) | 1 | 0 | 0 | 0 |
| [AiRequestLimiterTest](../src/test/java/com/recipick/domain/recipe/service/AiRequestLimiterTest.java) | 3 | 0 | 0 | 0 |
| 합계 | 19 | 0 | 0 | 0 |

19건 전부가 동시 요청 테스트는 아니며 미리보기·확정의 일반 흐름과 롤백 검증을 포함합니다.

```bash
./gradlew test \
  --tests 'com.recipick.domain.recipe.facade.AutoCartFacadeIntegrationTest' \
  --tests 'com.recipick.domain.recipe.service.RecipeBookmarkConcurrencyTest' \
  --tests 'com.recipick.domain.recipe.service.AiRequestLimiterTest'
```

**검증하지 않은 범위**

- `AutoCartFacadeIntegrationTest`는 `MemberService`·`MembershipService`를 목으로 둡니다. 동시 확정 결과는 실제 DB의 요청 행 잠금으로 얻은 것이지만, 회원 행 잠금의 실제 대기와 "탈퇴 커밋 대기 후 확정 거절"은 확인하지 않았습니다. 탈퇴 테스트는 거절 시 롤백만 확인합니다.
- 확정과 직접 장바구니 담기가 같은 상품에서 겹치는 경우는 레시피 테스트에 없습니다. 장바구니 자체의 동시 담기는 주문 도메인의 `CartAddConcurrencyTest`(3건) 범위입니다.
- AI 한도는 Redis 하나를 공유하는 설계로 여러 서버에서도 같은 값을 세지만 여러 앱 인스턴스로 실행하지 않았습니다. 고정 구간이라 구간 경계에서 짧게 최대 10회까지 통과할 수 있습니다.

## 6. 결제 도메인: 승인·만료·취소·웹훅·포인트

| 항목 | 내용 |
| --- | --- |
| 작성 담당 | 정윤 |
| 코드 기준 | `dev`의 `9172f6d`(2026-11-04) |
| 실행 근거 | 2026-11-04 같은 커밋의 `./gradlew test --tests 'com.recipick.domain.payment.*'` 136개 클래스 1,673건(실패 0·오류 0·건너뜀 0). Gradle 테스트 결과 XML 기준. 실행 환경은 아래 문단 참고 |
| 설계 근거 | [결제·웹훅·환불 정합성 ADR](adr/payment-consistency.md) |

실행 환경: Docker를 쓸 수 없는 작성자 작업 환경이라 `IntegrationTestSupport`의 Testcontainers 연결만 로컬 MySQL 8.0(`innodb_lock_wait_timeout=5`)·Redis로 바꿔 실행한 결과입니다. 이 연결 변경은 커밋하지 않았습니다. 저장소 기본 구성(CI 포함)은 Testcontainers MySQL 8.4(`--innodb-lock-wait-timeout=5`)·Redis 7.4이며, 이 PR의 CI `test` 작업이 같은 범위를 그 환경에서 다시 실행합니다. 두 결과를 같은 환경의 결과로 보지 않습니다.

### 6-1. 무엇을 보호하는가

결제는 같은 사실(승인·실패·취소)이 확정 API·웹훅·만료 작업·Fallback Sweep으로 반복·동시에 들어옵니다. 하나의 사실은 한 번만 반영돼야 합니다.

| 경합 | 보호할 불변 조건 | 공유 잠금·제약 |
| --- | --- | --- |
| 승인 vs 만료 | 결제는 `PAID`·`FAILED(EXPIRED)` 중 하나로만 끝남. 재고·포인트 1회 복구, 만료 뒤 승인은 SYSTEM 환불 1건 | 결제 행 → 주문 행 → `MEMBER_POINT` → `POINT_EARNING` → 재고 |
| 취소 vs 웹훅 | 환불 행 1건, PortOne 취소 1건, 결제 `CANCELED`·주문 `REFUNDED`·재고 복구 1회 | 결제 행 + `refund.idempotency_key`·`portone_cancellation_id` UNIQUE + `webhook_event.webhook_id` UNIQUE |
| 포인트 사용 vs 만료 | 만료 포인트는 쓰이지 않음. 잔액은 음수가 되지 않고 소멸 이력은 1회 | `MEMBER_POINT` → `POINT_EARNING`(id 오름차순) + `member_point.member_id` UNIQUE |
| 동일 회차 자동청구 | 같은 회차·같은 시도의 PG POST는 1회. 결과 불명이면 같은 결제 ID로 재조회만 | 회차 UNIQUE + 시도 PK 잠금 선점(READY → DISPATCHING, 임대) |
| 빌링키 등록·교체 | 회원당 `ACTIVE` 키 1개, 같은 PortOne 키는 한 회원에게만 | 회원 행 + `uk_billing_key_portone_billing_key` |
| 회원 탈퇴 vs 정산 | 미해결 결제·환불·웹훅·멤버십 회차가 있으면 탈퇴 거절. 이미 접수된 거래의 후속 처리는 막히지 않음 | 회원 행 → 결제(PK 오름차순) → 환불·웹훅 FOR SHARE |

### 6-2. 후보와 선택 이유

| 후보 | 장점 | 이 요구사항에서의 한계 |
| --- | --- | --- |
| PG 호출을 포함한 한 트랜잭션 | 구현 단순 | PG 응답을 기다리는 동안 결제·주문 잠금 유지. PG 성공 뒤 DB 롤백 시 돈만 나감 |
| 낙관적 락 | 대기 없음 | 컨벤션상 금지. 충돌 뒤 PortOne 재조회부터 다시 해야 함 |
| Redis 분산 락으로 스케줄러·Worker 단일 실행 | 중복 실행 감소 | TTL이 지나면 두 인스턴스가 함께 실행. 이중 결제를 막는 수단이 되지 못함 |
| **결제 행 비관적 락 + 고정 잠금 순서 + 신규 행 UNIQUE + 업무 행 멱등 키, PG 호출은 잠금 밖(선택)** | 잠근 뒤 현재 상태로 판정해 반복 전달이 멱등. 서로 다른 행을 잠근 경합은 UNIQUE가 최종 판정 | 같은 결제의 요청은 대기. TX1·TX2 사이에 다른 경로가 끝낸 경우를 TX2가 판정해야 함 |

상품 결제의 잠금 대상이 결제 행인 이유는 확정 API·웹훅·만료·취소·마트 거절·외부 취소가 모두 같은 결제를 바꾸기 때문입니다. 회원 행은 잠그지 않습니다(1절의 "회원 잠금이 없는 기존 결제 흐름").
환불 행은 따로 잠그지 않고 결제 잠금 아래에서만 바꿉니다. 멤버십은 회원 → 이용권 → 회차 → 시도 → 환불 순서로, 범위 `FOR UPDATE` 대신 ID를 읽은 뒤 한 행씩 PK로 잠급니다.

### 6-3. 코드에서의 적용

**잠금 조회**(`src/main/java/com/recipick/domain/payment/repository`)

| Repository 메서드 | 잠금 | 조건 |
| --- | --- | --- |
| [PaymentRepository](../src/main/java/com/recipick/domain/payment/repository/PaymentRepository.java).`findByOrderIdForUpdate` | `PESSIMISTIC_WRITE` | `p.orderId = :orderId` |
| `PaymentRepository.findByIdForUpdate` | `PESSIMISTIC_WRITE` | `p.id = :id` |
| `PaymentRepository.findAllByIdInForUpdate` | `PESSIMISTIC_WRITE` | `p.id IN :ids ORDER BY p.id` |
| [MemberPointRepository](../src/main/java/com/recipick/domain/payment/repository/MemberPointRepository.java).`findByMemberIdForUpdate` | `PESSIMISTIC_WRITE` | `mp.memberId = :memberId` |
| [PointEarningRepository](../src/main/java/com/recipick/domain/payment/repository/PointEarningRepository.java).`findByOrderIdForUpdate`·`findExpiredForUpdate`·`findUsableForUpdate`·`findAllByIdInForUpdate` | `PESSIMISTIC_WRITE` | 주문 ID 또는 회원 ID·상태·만료일, `ORDER BY e.id ASC` |
| [PointUsageRepository](../src/main/java/com/recipick/domain/payment/repository/PointUsageRepository.java).`findAllByOrderIdForUpdate` | `PESSIMISTIC_WRITE` | `u.orderId = :orderId ORDER BY u.id ASC` |
| [WebhookEventRepository](../src/main/java/com/recipick/domain/payment/repository/WebhookEventRepository.java).`findByIdForUpdate` | `PESSIMISTIC_WRITE` | Worker 선점·결과 기록 |
| `WebhookEventRepository.findOpenForWithdrawalForShare` | `PESSIMISTIC_READ` | 탈퇴 정산, `idx_webhook_event_portone_payment_id_status` 사용 |
| [RefundRepository](../src/main/java/com/recipick/domain/payment/repository/RefundRepository.java).`findOpenForWithdrawalForShare` | `PESSIMISTIC_READ` | 탈퇴 정산, `REQUESTED` 또는 SYSTEM `FAILED` |
| [BillingKeyRepository](../src/main/java/com/recipick/domain/payment/repository/BillingKeyRepository.java)·[BillingKeyDeletionRepository](../src/main/java/com/recipick/domain/payment/repository/BillingKeyDeletionRepository.java).`findByIdForUpdate` | `PESSIMISTIC_WRITE` | PK |
| [MembershipPaymentRepository](../src/main/java/com/recipick/domain/payment/repository/MembershipPaymentRepository.java)·[MembershipPaymentAttemptRepository](../src/main/java/com/recipick/domain/payment/repository/MembershipPaymentAttemptRepository.java)·[MembershipRefundRepository](../src/main/java/com/recipick/domain/payment/repository/MembershipRefundRepository.java).`findByIdForUpdate` | `PESSIMISTIC_WRITE` | PK. 여러 행은 ID를 일반 조회로 읽고 오름차순으로 한 행씩 |

**UNIQUE 제약**(엔티티 기준, [ERD](erd.dbml)와 일치)

| 테이블 | 제약 | 막는 경합 |
| --- | --- | --- |
| `refund` | `idempotency_key`, `portone_cancellation_id` | 다른 결제에 같은 키 동시 취소, 같은 PortOne 취소의 중복 기록 |
| `webhook_event` | `webhook_id`(`uk_webhook_event_webhook_id`) | 같은 웹훅 재전송·동시 수신 |
| `payment` | `order_id`, `portone_payment_id` | 주문당 결제 1건 |
| `point_earning` | `order_id` | 주문당 적립 예정 1건 |
| `point_usage` | `(point_earning_id, order_id)` | 같은 적립 건의 같은 주문 사용 중복 |
| `member_point` | `member_id` | 처음 포인트를 쓰는 회원의 행 동시 생성 |
| `billing_key` | `portone_billing_key`(`uk_billing_key_portone_billing_key`) | 두 회원의 같은 키 동시 등록 |
| `billing_key_issue_request` | `issue_id` | 같은 발급 요청 중복 |
| `membership_payment` | `(membership_id, period_start_date)`, `idempotency_key` | 같은 회차 이중 생성, 같은 구독 요청 |
| `membership_payment_attempt` | `portone_payment_id`, `(membership_payment_id, attempt_no)` | 같은 시도 번호·같은 PG 결제 ID |
| `membership_refund` | `idempotency_key`, `portone_cancellation_id`, `pg_idempotency_key`, `retry_of_refund_id` | 같은 환불 요청·같은 PG 취소·같은 재실행 |

**경합별 적용**

```text
승인 vs 만료
  PaymentConfirmFacade / PaymentReconciliationFacade / PaymentExpirationFacade (트랜잭션 없음)
  → PortOne 조회(잠금 없음)
  → PaymentConfirmProcessor.process 또는 PaymentExpirationProcessor.expire
      결제 행 잠금 → 상태 재검사(IN_PROGRESS만 변경) → 주문 → 포인트 → 재고
  → 커밋 뒤 SYSTEM 환불 실행 예약(만료 뒤 승인·금액 불일치)

취소 vs 웹훅
  TX1 PaymentCancelProcessor.prepare: 결제 잠금 → 같은 키 환불 조회 → CANCEL_REQUESTED·환불 REQUESTED(saveAndFlush)
  PortOne 취소(잠금 없음, 키 recipick-refund-{환불 ID})
  TX2 PaymentCancelProcessor.apply: 결제 잠금 → 환불 COMPLETED·FAILED면 그 결과 반환, 아니면 반영
  웹훅 Worker: webhook_event 행 잠금 선점 → 커밋 → PortOne 조회 → 같은 apply(TX-B) → 결과 기록(TX-C)

포인트 사용 vs 만료
  주문 생성 reserveUsage / 만료 배치 expireMember(회원마다 새 트랜잭션)
  → MEMBER_POINT 존재 확인(잠그지 않는 조회)
  → 행이 없을 때만 INSERT ... ON DUPLICATE KEY UPDATE
  → MEMBER_POINT PESSIMISTIC_WRITE 잠금
  → 지연 만료(findExpiredForUpdate) → 사용 가능 적립 건(findUsableForUpdate, id 오름차순)

동일 회차 자동청구
  MembershipChargeDispatchProcessor: 시도 PK만 잠금 → READY이고 임대 안일 때만 DISPATCHING
  MembershipChargeExecutor: PG POST(잠금 없음) → 같은 결제 ID GET
  MembershipChargeResultProcessor: MEMBER → 이용권 → 회차 PK → 시도 PK 오름차순 → 판정 시각 재조회 → 반영

빌링키 등록·교체
  BillingKeyRegistrationFacade(NEVER): PortOne 검증(잠금 없음)
  BillingKeyRegistrationProcessor: 회원 잠금 → ACTIVE 조회 → 교체·saveAndFlush(UNIQUE 위반만 BILLING-002)

회원 탈퇴 vs 정산
  회원 잠금 → ProductPaymentSettlementChecker → PaymentService.existsUnsettledForWithdrawal(MANDATORY)
      결제 findAllByIdInForUpdate(PK 오름차순) → 환불 FOR SHARE → 웹훅 FOR SHARE
  → MembershipPaymentSettlementChecker(C-17) → 포인트 검사(마지막)
```

- 같은 키·같은 결제의 재전송은 결제 잠금 아래 환불 조회가 먼저 거르고, 다른 결제의 같은 키는 `PaymentService.requestMemberRefund`의 `saveAndFlush` UNIQUE 위반을 `COMMON-011`로 바꿉니다.
- 같은 키 재요청이 PortOne을 다시 부르는 것은 환불에 취소 ID가 없고 요청 + 3시간 안일 때뿐이며, 서버 키가 같아 PortOne에서는 한 건입니다.
- 웹훅 저장은 짧은 트랜잭션 하나이고 `webhook_id` UNIQUE 충돌은 트랜잭션 밖 `PortOneWebhookFacade`가 200(`DUPLICATE`)으로 바꿉니다.
- 탈퇴 정산의 판정 행은 잠금 읽기로 최신 커밋을 봅니다. 결제 확정·늦은 승인 환불·외부 취소는 회원을 잠그지 않으므로 회원 잠금만으로는 직렬화되지 않기 때문입니다.

### 6-4. 잠금 실패와 롤백

- 잠금 대기 시간 초과·교착은 결제 도메인 코드에서 업무 오류로 바꾸지 않습니다. 경로별로 다음과 같이 끝납니다.

| 경로 | 처리 | 결과 |
| --- | --- | --- |
| HTTP API(확정·취소·마트 거절) | 예외가 [GlobalExceptionHandler](../src/main/java/com/recipick/global/exception/GlobalExceptionHandler.java)까지 올라감. `PessimisticLockingFailureException`(하위 `CannotAcquireLockException` 포함)을 `409 COMMON-010`으로 응답 | 해당 트랜잭션 롤백. 자동 재시도 없음 |
| 웹훅 Worker TX-B, 만료·환불 재확인·SYSTEM·마트 거절 실행 | `TransientDataAccessException`으로 받아 재시도(웹훅 `RETRY_PENDING`, 만료 1분 뒤, 환불 재확인 재예약) | 상태는 미완료로 남고 다음 실행이 PortOne 조회부터 다시 함 |
| 웹훅 Worker TX-A(선점) | Worker가 작업 단위로 잡아 로그만 남김 | 큐 작업은 사라지지만 행이 `RECEIVED`로 남아 Fallback이 다시 등록 |
| 결제 완료 뒤 장바구니 정리 | 커밋 뒤 리스너가 예외를 끊음 | 결제 확정은 유지, 장바구니만 남음 |

- Hibernate 6.6의 MySQL 변환은 교착(SQLState 40001)을 `LockAcquisitionException`, 잠금 대기 초과(1205)를 `PessimisticLockException`으로 만들고, Spring은 각각 `CannotAcquireLockException`·`PessimisticLockingFailureException`으로 바꿉니다. 둘 다 `TransientDataAccessException` 하위입니다.
- InnoDB는 교착 시 희생 트랜잭션 전체를 롤백하므로 TX1의 주문 `CANCEL_REQUESTED`·결제 `CANCEL_REQUESTED`·환불 행은 남지 않습니다. 다만 이 경로를 교착으로 재현해 최종 상태를 확인한 테스트는 없습니다.
- `PaymentCancelFacade`·`PaymentService.requestMemberRefund`가 바꾸는 예외는 `DataIntegrityViolationException`(→ `COMMON-011`)뿐입니다. 같은 키 경합이 UNIQUE 충돌이 아니라 교착으로 끝나면 API 응답은 `COMMON-011`이 아니라 `COMMON-010`이 되고, Facade를 직접 부르는 테스트에서는 `CannotAcquireLockException`이 그대로 올라옵니다.

**알려진 문제 #437**

GitHub 이슈 #437은 `PaymentCancelAfterPayConcurrencyTest.sameKeyOnTwoPaidPayments`(두 `PAID` 결제에 같은 키로 동시 취소)가 CI에서 간헐적으로 `CannotAcquireLockException` ← `LockAcquisitionException`으로 실패한다고 보고합니다.
이 테스트의 `attempt`는 `BusinessException`만 결과로 바꾸므로 교착 예외는 스레드 결과 조회에서 실패로 드러납니다.
원인은 아직 확인되지 않았습니다. 로컬 반복 실행 230회는 통과했고, 2026-11-04 위 로컬 MySQL 8.0 전체 실행에서도 이 클래스 3건이 통과했습니다. 통과가 문제가 없다는 뜻은 아닙니다.
원인이 교착으로 확인되면 이 경합의 교착을 업무 오류로 바꿀지, TX1의 쿼리 순서를 바꿀지 결정합니다.

**검증하지 않은 범위**

- 운영 DB의 `innodb_lock_wait_timeout`은 확인하지 않았습니다. 로컬 MySQL 8.0 실행은 `innodb_lock_wait_timeout=5`로 Testcontainers 구성과 맞췄습니다.
- 여러 앱 인스턴스·실 PortOne 호출·부하 조건에서 실행한 결과가 아닙니다. PortOne은 테스트 스텁 서버 또는 목입니다.

### 6-5. 실제 동시 요청·실패 결과

아래 클래스는 모두 실제 MySQL·Redis를 쓰는 통합 테스트(`IntegrationTestSupport` 상속)이며 별도 스레드가 각자 커밋합니다.
어느 요청이 먼저 이길지는 고정하지 않고 최종 DB 상태로 판정합니다. `@RepeatedTest(3)`인 시나리오는 반복 횟수가 건수에 포함됩니다.

| 경합 | 시나리오 | DB 최종 결과 | 근거 |
| --- | --- | --- | --- |
| 승인 vs 만료 | 같은 결제에 승인 5건 동시 | 결제·주문 `PAID` 1회, 나머지 `ALREADY_PAID` | [PaymentConfirmConcurrencyTest](../src/test/java/com/recipick/domain/payment/facade/PaymentConfirmConcurrencyTest.java) |
| 승인 vs 만료 | 만료로 닫힌 결제에 늦은 승인 5건 동시 | SYSTEM 환불 1건, 결제·주문 그대로 | 같은 클래스 |
| 승인 vs 만료 | 결제 확정과 만료가 같은 결제를 동시에 잡음 | 결제 잠금으로 한쪽만 반영, 재고 이중 복구·환불 중복 없음 | [PaymentExpirationFlowIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/PaymentExpirationFlowIntegrationTest.java) |
| 승인 vs 만료 | 확정 API 흐름과 Paid 웹훅 처리가 동시 | 승인 1회, 주문 이력 2행, SYSTEM 환불 없음 | [PaymentWebhookConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/PaymentWebhookConcurrencyTest.java) |
| 취소 vs 웹훅 | 같은 `webhook_id` 5건 동시 수신 | 모두 200, 행 1개, 큐 작업 1개 | 같은 클래스 |
| 취소 vs 웹훅 | 결제 전 취소 5건 동시 | 재고 1회 복구, 나머지 멱등 응답 | [PaymentCancelConcurrencyTest](../src/test/java/com/recipick/domain/payment/facade/PaymentCancelConcurrencyTest.java) |
| 취소 vs 웹훅 | 같은 `PAID` 결제에 다른 키 취소 5건 | 환불 1건·PortOne 호출 1회, 나머지 `COMMON-006`, 재고 1회 복구 | [PaymentCancelAfterPayConcurrencyTest](../src/test/java/com/recipick/domain/payment/facade/PaymentCancelAfterPayConcurrencyTest.java) |
| 취소 vs 웹훅 | 같은 `PAID` 결제에 같은 키 2건 | PortOne은 같은 서버 키로 2회 호출될 수 있음, 환불 1건·취소·재고 복구 1회 | 같은 클래스 |
| 취소 vs 웹훅 | 두 `PAID` 결제에 같은 키 2건 | 1건 성공, 1건 `COMMON-011`, 진 쪽 결제·주문 `PAID`·이력 없음(#437 간헐 실패) | 같은 클래스 |
| 취소 vs 웹훅 | 취소 API가 PortOne 응답을 기다리는 사이 Cancelled 웹훅이 먼저 완료 | 늦은 TX2는 멱등, 환불·재고 복구 1회 | [RefundWebhookConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/RefundWebhookConcurrencyTest.java) |
| 취소 vs 웹훅 | 서로 다른 웹훅 2건이 같은 콘솔 전액 취소를 동시 처리 | 둘 다 `PROCESSED`, `EXTERNAL` 환불·재고 복구·주문 이력 1회 | [ExternalCancellationConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/ExternalCancellationConcurrencyTest.java) |
| 취소 vs 웹훅 | 같은 키·다른 키 마트 거절 5건, 거절 vs 수락, 거절 vs 회원 취소, 같은 취소 웹훅 2건 | `ADMIN` 환불·거절 전이·감사 1회, 진행 중 환불 1건, 한쪽만 성공 | [MartRejectionConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MartRejectionConcurrencyTest.java) |
| 포인트 사용 vs 만료 | 만료 배치와 포인트 사용 동시 | 만료 포인트 미사용, 소멸 이력 1회, 잔액 0(음수 없음) | [PointExpirationConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/PointExpirationConcurrencyTest.java) |
| 포인트 사용 vs 만료 | 두 서버의 만료 배치가 같은 회원 처리 / 포인트 행 없는 회원 동시 생성 | 한쪽만 소멸, 다른 쪽 0 / 행 1개·잔액 0 | 같은 클래스 |
| 포인트 사용 vs 만료 | 1,000P 회원의 두 주문이 700P씩 동시 예약 | 1건 성공, 1건 `POINT-001`, 잔액·적립 건 잔량 300P | [PointReservationConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/PointReservationConcurrencyTest.java) |
| 동일 회차 자동청구 | 같은 멱등 키 동시 구독, 같은 READY 동시 실행, READY 만료 vs 선점 | 이용권·회차·시도 1건, POST 1회. 진 쪽은 GET·POST 없이 `IN_PROGRESS`. 취소된 시도의 POST 0 | [MembershipChargeConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipChargeConcurrencyTest.java) |
| 동일 회차 자동청구 | 두 스케줄러가 같은 갱신 후보, 같은 갱신 READY를 세 Worker가 실행 | 회차·시도 1개, POST 1회, 연장 1회 | [MembershipRenewalConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipRenewalConcurrencyTest.java) |
| 동일 회차 자동청구 | 같은 키 환불 5건, 실행기·웹훅·복구 스캔이 같은 성공 취소 반영, 임대 끝난 옛 실행자 | 환불 1건·취소 작업 1개, 완료·종료 1회, 추가 POST 없음, 옛 실행자는 기록을 덮어쓰지 못함 | [MembershipRefundConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipRefundConcurrencyTest.java) |
| 빌링키 등록·교체 | 같은 회원 카드 2장 동시, 같은 토큰 키 2개, 두 회원 같은 키 | `ACTIVE` 1·`REPLACED` 1 / 1건만 등록·`BILLING-007` / 1명만 통과·`BILLING-002`, 로그에 키 원문 없음 | [BillingKeyRegistrationConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/BillingKeyRegistrationConcurrencyTest.java) |
| 빌링키 등록·교체 | 키 폐기 반영 vs 새 카드 등록 | 어느 쪽이 먼저 잠가도 A `REVOKED`·B `ACTIVE` | [MembershipRenewalConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipRenewalConcurrencyTest.java) |
| 회원 탈퇴 vs 정산 | 탈퇴 스냅샷 뒤 커밋된 SYSTEM 환불·웹훅 | 잠금 읽기로 잡아 `PAYMENT-012` | [ProductPaymentSettlementConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/ProductPaymentSettlementConcurrencyTest.java) |
| 회원 탈퇴 vs 정산 | 탈퇴가 결제를 잠근 동안 늦은 승인 환불·다른 회원 결제·웹훅 Worker | 늦은 승인 환불은 대기 후 진행, 다른 회원·Worker는 교착 없이 완료 | 같은 클래스 |
| 회원 탈퇴 vs 정산 | 탈퇴 vs 멤버십 회차 생성 | 잠금 순서대로 한쪽 결과만 남음. 잠금 전 일반 조회는 뒤 커밋을 못 봄(전제 확인) | [MembershipPaymentQueryConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipPaymentQueryConcurrencyTest.java) |
| 회원 탈퇴 vs 정산 | 즉시 해지 vs 갱신 접수 | 먼저 회원을 잠근 쪽 결과만 남음(0원 환불 종료 또는 `MEMBERSHIP-010`) | [MembershipRefundConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipRefundConcurrencyTest.java) |

**실패·복구 경로**(실제 MySQL·Redis)

| 시나리오 | DB 최종 결과 | 근거 |
| --- | --- | --- |
| Worker 선점이 잠금 대기 초과로 실패 | 이벤트 `RECEIVED` 유지, Fallback 재등록 뒤 처리 | [PaymentRecoveryIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/PaymentRecoveryIntegrationTest.java) |
| Worker가 선점 뒤 죽어 `PROCESSING` 잔존 | 10분 뒤 Fallback 재등록, 처리권 2번으로 완료 | 같은 클래스 |
| 만료·SYSTEM 환불 작업이 Redis에서 사라짐 | 재조회 구간·재실행 간격 뒤 Fallback 재등록, 완료 | [PaymentExpirationFlowIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/PaymentExpirationFlowIntegrationTest.java), [SystemRefundFlowIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/SystemRefundFlowIntegrationTest.java) |
| PortOne 취소 `FAILED`·`REJECTED` | Facade 예외 전에 TX2 커밋, 결제·주문 `PAID` 복귀·환불 `FAILED` 유지 | [PaymentCancelFacadeIntegrationTest](../src/test/java/com/recipick/domain/payment/facade/PaymentCancelFacadeIntegrationTest.java) |
| 장바구니 정리가 잠금 대기 초과 | 결제 확정 유지, 장바구니만 남음 | [PaymentCartCleanupIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/PaymentCartCleanupIntegrationTest.java) |

| 검증 클래스(실제 DB) | 테스트 | 실패 | 오류 | 건너뜀 |
| --- | --- | --- | --- | --- |
| [PaymentConfirmConcurrencyTest](../src/test/java/com/recipick/domain/payment/facade/PaymentConfirmConcurrencyTest.java) | 2 | 0 | 0 | 0 |
| [PaymentCancelConcurrencyTest](../src/test/java/com/recipick/domain/payment/facade/PaymentCancelConcurrencyTest.java) | 1 | 0 | 0 | 0 |
| [PaymentCancelAfterPayConcurrencyTest](../src/test/java/com/recipick/domain/payment/facade/PaymentCancelAfterPayConcurrencyTest.java) | 3 | 0 | 0 | 0 |
| [PaymentCancelFacadeIntegrationTest](../src/test/java/com/recipick/domain/payment/facade/PaymentCancelFacadeIntegrationTest.java) | 4 | 0 | 0 | 0 |
| [PaymentWebhookConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/PaymentWebhookConcurrencyTest.java) | 2 | 0 | 0 | 0 |
| [RefundWebhookConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/RefundWebhookConcurrencyTest.java) | 1 | 0 | 0 | 0 |
| [ExternalCancellationConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/ExternalCancellationConcurrencyTest.java) | 1 | 0 | 0 | 0 |
| [MartRejectionConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MartRejectionConcurrencyTest.java) | 5 | 0 | 0 | 0 |
| [PaymentExpirationFlowIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/PaymentExpirationFlowIntegrationTest.java) | 5 | 0 | 0 | 0 |
| [SystemRefundFlowIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/SystemRefundFlowIntegrationTest.java) | 4 | 0 | 0 | 0 |
| [PaymentRecoveryIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/PaymentRecoveryIntegrationTest.java) | 2 | 0 | 0 | 0 |
| [PaymentCartCleanupIntegrationTest](../src/test/java/com/recipick/domain/payment/integration/PaymentCartCleanupIntegrationTest.java) | 7 | 0 | 0 | 0 |
| [PointExpirationConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/PointExpirationConcurrencyTest.java) | 3 | 0 | 0 | 0 |
| [PointReservationConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/PointReservationConcurrencyTest.java) | 2 | 0 | 0 | 0 |
| [MembershipChargeConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipChargeConcurrencyTest.java) | 9 | 0 | 0 | 0 |
| [MembershipRenewalConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipRenewalConcurrencyTest.java) | 13 | 0 | 0 | 0 |
| [MembershipRefundConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipRefundConcurrencyTest.java) | 8 | 0 | 0 | 0 |
| [MembershipPaymentQueryConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/MembershipPaymentQueryConcurrencyTest.java) | 3 | 0 | 0 | 0 |
| [BillingKeyRegistrationConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/BillingKeyRegistrationConcurrencyTest.java) | 13 | 0 | 0 | 0 |
| [ProductPaymentSettlementConcurrencyTest](../src/test/java/com/recipick/domain/payment/integration/ProductPaymentSettlementConcurrencyTest.java) | 6 | 0 | 0 | 0 |
| 합계 | 94 | 0 | 0 | 0 |

94건 전부가 동시 요청 테스트는 아니며 순차 흐름·실패 복구 검증을 포함합니다. 결제 패키지 전체(136개 클래스 1,673건)에서 위 20개 클래스만 추렸습니다.

잠금 실패 분기의 Mockito 단위 테스트는 실제 잠금 경합이 아니라 `CannotAcquireLockException`을 던지도록 고정한 분기 확인입니다. 위 합계에 넣지 않습니다.

| 단위 테스트(Mockito) | 확인하는 분기 |
| --- | --- |
| [PaymentConfirmFacadeTest](../src/test/java/com/recipick/domain/payment/facade/PaymentConfirmFacadeTest.java) | Processor가 잠금 대기 초과로 실패하면 SYSTEM 환불 예약 없이 예외를 그대로 올림 |
| [PaymentReconciliationFacadeTest](../src/test/java/com/recipick/domain/payment/facade/PaymentReconciliationFacadeTest.java) | 잠금 대기 초과 같은 일시적 DB 장애는 `RETRY_PENDING` |
| [PaymentExpirationFacadeTest](../src/test/java/com/recipick/domain/payment/facade/PaymentExpirationFacadeTest.java) | 만료 Processor가 잠금 대기 초과로 실패하면 1분 뒤 다시 조회 |
| [PaymentFallbackSchedulerTest](../src/test/java/com/recipick/domain/payment/scheduler/PaymentFallbackSchedulerTest.java)·[PaymentDelayQueueWorkerTest](../src/test/java/com/recipick/domain/payment/scheduler/PaymentDelayQueueWorkerTest.java) | 상태별 기준 시각의 재등록, 작업 종류 라우팅·작업별 예외 격리 |

```bash
./gradlew test \
  --tests 'com.recipick.domain.payment.facade.PaymentConfirmConcurrencyTest' \
  --tests 'com.recipick.domain.payment.facade.PaymentCancelConcurrencyTest' \
  --tests 'com.recipick.domain.payment.facade.PaymentCancelAfterPayConcurrencyTest' \
  --tests 'com.recipick.domain.payment.facade.PaymentCancelFacadeIntegrationTest' \
  --tests 'com.recipick.domain.payment.integration.PaymentWebhookConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.RefundWebhookConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.ExternalCancellationConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.MartRejectionConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.PaymentExpirationFlowIntegrationTest' \
  --tests 'com.recipick.domain.payment.integration.SystemRefundFlowIntegrationTest' \
  --tests 'com.recipick.domain.payment.integration.PaymentRecoveryIntegrationTest' \
  --tests 'com.recipick.domain.payment.integration.PaymentCartCleanupIntegrationTest' \
  --tests 'com.recipick.domain.payment.integration.PointExpirationConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.PointReservationConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.MembershipChargeConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.MembershipRenewalConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.MembershipRefundConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.MembershipPaymentQueryConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.BillingKeyRegistrationConcurrencyTest' \
  --tests 'com.recipick.domain.payment.integration.ProductPaymentSettlementConcurrencyTest'
```

## 7. 다른 도메인의 작성 위치

| 영역 | 담당 | 추가할 내용·현재 문서 상태 |
| --- | --- | --- |
| 상품·재고 | 태헌 | 작성: [상품 ADR](adr/product-domain.md) 3-4절(재고 차감·복구·관리자 조정의 잠금 대상·순서)·5절(`ProductStockConcurrencyTest` 등 검증 결과) |
| 레시피 자동 구성 | 보민 | 5절 작성·리뷰 대기: 확정 중복·탈퇴 거절 롤백·찜·AI 한도. 실제 회원 잠금 대기와 확정·직접 담기 교차 경합은 미검증 |
| 주문 | 준희 | 작성: [장바구니·주문 ADR](adr/cart-order.md) 3절(결정별 근거)·5절(검증 결과). 담기는 회원 행 잠금 + 조건부 UPDATE + `UNIQUE cart_item(member_id, product_id)`, 주문 생성은 회원 행 잠금 + `UNIQUE orders(member_id, idempotency_key)` + `UNIQUE orders(pending_member_id)`(결제 대기일 때만 값이 있음)로 중복 방어, 결제 결과·수락·거절·배송은 주문 행 잠금 뒤 상태로 판정, 주문 종료·재고 복구·배송완료 적립 확정은 `MANDATORY`로 같은 트랜잭션에서만 실행. 주문 도메인 34개 클래스 405개 통과(2026-11-03, `742a694`) |
| 결제·환불·포인트 | 정윤 | 작성·리뷰 대기: [결제 절](#6-결제-도메인-승인만료취소웹훅포인트)(승인 vs 만료·취소 vs 웹훅·포인트 사용 vs 만료·동일 회차 자동청구·빌링키 등록·탈퇴 vs 정산)과 [결제 정합성 ADR](adr/payment-consistency.md). 결제 행 잠금 + `PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고` + `refund.idempotency_key`·`webhook_event.webhook_id`·`portone_cancellation_id` UNIQUE + PG 호출 TX1 → TX2 분리. 결제 패키지 136개 클래스 1,673건 통과(2026-11-04, `9172f6d`, 로컬 MySQL 8.0). 알려진 간헐 실패 #437(원인 미확정) |

각 담당자는 보호할 조건 → 후보·선택 이유 → 실제 잠금·제약 → 실패 처리 → 재현 명령·수치·테스트 근거 순으로 추가합니다.
`작성 대기`는 문서 취합 상태이며 해당 기능 코드의 미구현 여부를 뜻하지 않습니다.

## 8. 관련 규칙

- [요구사항](requirement.md), [트랜잭션·동시성 컨벤션](convention.md#트랜잭션동시성).
- [회원 Repository](../src/main/java/com/recipick/domain/member/repository/MemberRepository.java), [배송지 Facade](../src/main/java/com/recipick/domain/member/facade/MemberAddressFacade.java), [멤버십 지급 Facade](../src/main/java/com/recipick/domain/member/facade/MembershipGrantFacade.java).
- [주문 회원 잠금 테스트](../src/test/java/com/recipick/domain/order/facade/OrderCreateMemberLockTest.java), [회원 탈퇴 기반 테스트](../src/test/java/com/recipick/domain/member/service/MemberWithdrawalFoundationIntegrationTest.java).
- [인증 근거](adr/authentication.md), [아키텍처](adr/layered-architecture.md), [문서 목차](README.md).
