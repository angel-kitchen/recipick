# 인증·인가 선택 근거와 검증 결과

회원·관리자 API는 액세스 JWT를 Bearer 헤더로 검증하고, 재발급은 HttpOnly 쿠키와 Redis의 리프레시 세션으로 관리합니다.
회원 토큰은 DB의 탈퇴 여부도 확인합니다. HTTP 세션을 사용하지 않지만, 서버 상태를 전혀 저장하지 않는 구조는 아닙니다.

| 항목 | 내용 |
| --- | --- |
| 담당 | 채빈 |
| 작성·검증일 | 2026-10-26 |
| 코드 기준 | `origin/dev`의 `a169297`을 반영한 문서 브랜치, 병합 커밋 `98a4465` |
| 관련 구현 | [탈퇴 기반 PR #192](https://github.com/angel-kitchen/recipick/pull/192) 포함 |
| 확인 환경 | Java 17, Spring Boot 3.5.16, 테스트 전용 MySQL 8.4·Redis 7.4, MockMvc |
| 상태 | 현재 구현과 대상 테스트 확인 완료, 문서 리뷰 대기 |

## 1. 상황

일반 회원과 관리자는 서로 다른 API·권한을 사용합니다. 회원은 본인 데이터만 접근하고,
마트 관리자는 소속 마트 범위, 슈퍼 관리자는 허용된 전체 관리자 기능을 사용해야 합니다.
프론트는 API를 호출하면서 로그인 상태를 유지하고, 비밀번호를 매 요청마다 보내지 않아야 합니다.

주요 조건은 짧은 액세스 토큰 수명, 재발급·로그아웃 시 서버의 세션 제어,
로그인 실패 제한, 탈퇴 회원 차단, 일관된 인증·인가 오류 응답입니다.

## 2. 후보와 선택

| 후보 | 장점 | 비용·한계 |
| --- | --- | --- |
| 서버 HTTP 세션 | 서버에서 세션 폐기를 통해 접근을 제어하기 쉬움 | 여러 앱 인스턴스에서 공유할 저장소와 쿠키 기반 인증 정책이 필요 |
| 액세스 JWT만 사용 | 서명·만료 확인으로 API 요청을 인증 가능 | 만료 전 폐기·긴 로그인 유지·재사용 제어를 별도로 풀어야 함 |
| **짧은 액세스 JWT + Redis 리프레시 세션(선택)** | API 인증과 장기 로그인 유지·세션 폐기를 나눠 관리 | Redis 상태 관리와 장애 처리, JWT 수명 중 권한 변경·탈퇴 확인 정책이 필요 |

프론트 API 인증은 Bearer 토큰으로 통일하고, 재발급·로그아웃은 서버에 남긴 세션으로 제어하는 방식을 선택했습니다.
JWT라는 이유만으로 즉시 로그아웃·권한 갱신·CSRF 방어가 자동 해결되는 것으로 보지 않습니다.

## 3. 현재 구현과 이유

### 3-1. 비밀번호와 로그인 실패 제한

[SecurityConfig](../../src/main/java/com/recipick/global/config/SecurityConfig.java)의 BCryptPasswordEncoder로 비밀번호를 검증합니다.
비밀번호 해시 비교는 로그인에서 수행하고 이후 요청은 토큰으로 인증합니다.
BCrypt는 비밀번호 검증 비용을 의도적으로 두는 방식이므로 로그인 처리량은 별도 측정 대상입니다.
[Spring Security 비밀번호 저장 설명](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

[MemberLoginService](../../src/main/java/com/recipick/domain/member/service/MemberLoginService.java)와
[AdminLoginService](../../src/main/java/com/recipick/domain/member/service/AdminLoginService.java)는 없는 계정·비밀번호 오류를 각각 동일한 자격 증명 오류로 응답합니다.
없는 계정도 더미 비밀번호 해시를 비교해 계정 존재 여부에 따른 시간 차이를 줄입니다.

[RedisLoginAttemptStore](../../src/main/java/com/recipick/infra/redis/RedisLoginAttemptStore.java)는 Lua로 실패 증가·잠금 판정·TTL 갱신을 원자적으로 처리합니다.
회원·관리자 키 공간을 구분하고 로그인 식별자는 해시해 Redis 키를 만듭니다.

- 세 번째 연속 실패부터 10분 잠금, 잠금 중에는 올바른 비밀번호도 거절합니다.
- 잠금 전 실패 기록은 실패할 때마다 10분 TTL을 갱신합니다. 로그인 성공 시 잠기지 않은 실패 기록을 제거합니다.
- DB가 같은 계정으로 비교하는 이메일·아이디 표기는 저장된 식별자를 기준으로 실패 횟수를 모읍니다.
- 성공 처리도 Lua로 잠금 상태를 확인하므로 동시 실패로 생긴 잠금을 성공 요청이 지우지 않습니다.

Lua 실행의 원자성은 실패 횟수·세션 교체를 여러 명령으로 분리할 때의 경합을 줄입니다.
[Redis Lua 실행 설명](https://redis.io/docs/latest/develop/programmability/eval-intro/)

### 3-2. 액세스 토큰과 인증 필터

[JwtTokenProvider](../../src/main/java/com/recipick/global/security/JwtTokenProvider.java)는 서명·용도·만료·필수 클레임·주체 형식을 검증합니다.
발급 시각 검증과 만료 검증에는 서버 간 시계 차이를 위한 5초 허용 범위가 있습니다.

| 항목 | 현재 정책 |
| --- | --- |
| 액세스 수명 | 설정값 30분 |
| 전달 | `Authorization: Bearer <accessToken>` |
| 주체 | `MEMBER` / `ADMIN`, 주체 ID |
| 관리자 범위 | 관리자 ID·역할·소속 마트 |
| 세션·토큰 식별 | 로그인 세션 `sid`, 개별 토큰 `jti`, 용도 `ACCESS` / `REFRESH` |
| 서명 키 | Base64 키를 HMAC SecretKey로 구성. 로컬 외 환경에서는 `JWT_SECRET` 필수 |

[JwtAuthenticationFilter](../../src/main/java/com/recipick/global/security/JwtAuthenticationFilter.java)에서 액세스 토큰을 한 번 해석해
SecurityContext의 Principal에 넣습니다. 컨트롤러는 `@LoginMember`·`@LoginAdmin`으로 식별자를 받습니다.
인증 필터는 공개 요청과 CORS preflight를 제외하며, 공개 경로는 잘못된 Bearer 토큰이 있어도 JWT 오류로 막지 않습니다.

회원 토큰은 [MemberStatusChecker](../../src/main/java/com/recipick/global/security/MemberStatusChecker.java)를 통해
[MemberStatusService](../../src/main/java/com/recipick/domain/member/service/MemberStatusService.java)가 회원 존재·탈퇴 여부를 확인합니다.
공통 보안이 회원 Repository를 직접 참조하지 않도록 인터페이스로 연결했고, 존재 조회로 회원 엔티티를 선로딩하지 않습니다.
없는 회원과 탈퇴 회원은 인증 단계에서 `401 AUTH-011`로 거절합니다.

### 3-3. 리프레시 회전과 로그아웃

리프레시 JWT의 수명은 설정값 14일이며 원문은 HttpOnly 쿠키로 전달합니다.
[RedisRefreshTokenStore](../../src/main/java/com/recipick/infra/redis/RedisRefreshTokenStore.java)는
주체 유형·회원 또는 관리자 ID·로그인 세션 `sid`별로 SHA-256 해시와 TTL을 저장합니다.
토큰은 추측하기 어려운 값이므로 세션 비교용 해시를 사용하며, 비밀번호 해싱과 용도를 구분합니다.

재발급 흐름은 [TokenManagementService.reissue](../../src/main/java/com/recipick/domain/member/service/TokenManagementService.java)에서 관리합니다.

1. 쿠키 JWT의 서명·용도·만료와 세션 정보를 확인합니다.
2. 회원은 탈퇴 여부, 관리자는 현재 활성 상태·역할·마트 정보를 DB에서 확인합니다.
3. 같은 세션 `sid`로 새 액세스·리프레시 토큰을 만듭니다.
4. Lua에서 기존 해시가 일치할 때만 새 해시와 TTL로 교체합니다.
5. 교체 성공일 때만 새 토큰과 쿠키를 반환합니다. 이전 토큰 재사용은 `AUTH-011`로 거절합니다.

현재 회전은 새 발급 시각부터 14일로 TTL을 갱신합니다. 별도의 로그인 세션 절대 수명 상한은 두지 않습니다.
이전 리프레시 토큰을 거절하는 동작과, 재사용 시 해당 회원의 모든 로그인 세션을 폐기하는 동작은 구분합니다. 후자는 현재 구현 범위가 아닙니다.

로그아웃은 액세스 토큰의 `sid`로 해당 세션을 삭제하고 쿠키를 만료시킵니다.
회전 중에도 같은 Redis 키를 사용하므로 로그아웃 뒤 교체된 세션이 남지 않게 합니다.
현재 세션 폐기가 다른 기기 전체 세션 폐기를 의미하지는 않습니다.
비밀번호 변경은 별도로 MEMBER.refresh_token_version을 비밀번호 해시와 함께 갱신합니다.
리프레시 JWT의 refreshVersion과 DB 버전이 다르면 재발급을 거절하고, 기존 액세스 토큰에는 이 버전을 적용하지 않습니다.
기존 토큰의 버전 누락은 0으로 해석합니다. Redis 키 정리와 경합·실패 정책은 비밀번호 변경 API 문서를 따릅니다.
기발급 액세스 토큰은 별도 블랙리스트 없이 만료까지 유효하며, 탈퇴 회원은 DB 상태 검사로 차단합니다.

### 3-4. 쿠키·CORS와 브라우저 경계

[AuthCookieFactory](../../src/main/java/com/recipick/domain/member/service/AuthCookieFactory.java)는
`HttpOnly`, `SameSite=Strict`, `Path=/api/auth` 쿠키를 발급하고 같은 속성으로 삭제합니다.
`Secure`는 기본 true, 로컬 프로필에서는 `AUTH_COOKIE_SECURE`로 조정합니다.
프론트와 API의 실제 배포 주소·HTTPS·브라우저 동작은 준희 담당 배포와 함께 확인해야 합니다.

CORS는 지정한 Origin과 자격 증명을 허용합니다. 서로 다른 출처에서 쿠키를 주고받을 때는
로그인·재발급·로그아웃 요청의 credentials 설정도 필요합니다.
프론트의 액세스 토큰 저장 위치는 이 백엔드 코드가 확정하는 사항이 아닙니다.

현재 SecurityConfig는 CSRF 보호를 비활성화합니다. Bearer 요청과 달리 리프레시 쿠키는 브라우저가 자동 전송하므로,
`SameSite`와 실제 배포 주소·쿠키 인증 경로를 함께 검토해야 합니다.
`STATELESS`나 CORS 설정만으로 CSRF 위험이 없어지는 것은 아닙니다. 교차 사이트 쿠키 정책으로 바꿀 때는 CSRF 방어도 재검토합니다.
[Spring Security CSRF 설명](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

### 3-5. 인가와 오류 계약

| 상황 | 현재 응답·처리 |
| --- | --- |
| 보호 API에 인증 없음 | `401 AUTH-001` |
| 잘못된 토큰·헤더·용도 | `401 AUTH-002` |
| 만료된 액세스 토큰 | `401 AUTH-003` |
| 회원·관리자 API 주체 불일치 | `403 AUTH-004` |
| 슈퍼 관리자 전용 기능에 마트 관리자 접근 | `403 AUTH-006` |
| 회원 / 관리자 자격 증명 불일치 | `401 AUTH-007` / `401 AUTH-008` |
| 비활성 관리자 로그인 | `403 AUTH-009` |
| 잘못된 재발급·폐기 세션·탈퇴/없는 회원 인증 | `401 AUTH-011` |
| 로그인 잠금 | `403 AUTH-012` |

마트 범위와 리소스 소유권은 해당 업무에서도 검사합니다. 다른 회원 소유 데이터는 도메인 정책대로 404를 사용합니다.
공개 API와 운영 상태·지표 경로의 규칙은 구분하며 Actuator 공개 범위는 [모니터링 문서](../monitoring.md)를 따릅니다.
관리자 토큰에 회원 탈퇴 상태 조회를 적용하지 않습니다. 기발급 관리자 토큰의 권한을 모든 API에서 DB로 다시 읽는 구조도 아닙니다.
관리자 활성 상태·현재 역할의 확인 시점은 재발급과 각 업무의 검증 범위를 구분합니다.

## 4. 트레이드오프와 후속 범위

JWT 검증에 더해 Redis 세션·실패 기록과 회원 상태 DB 조회가 필요합니다.
Redis 장애 시 재발급·로그아웃을 성공으로 간주하지 않으며, 회원 상태 확인에도 DB 가용성이 영향을 줍니다.
탈퇴 상태 캐시와 조회 최적화는 차단 시점·부하를 측정해 판단할 별도 작업입니다.

비밀번호 변경 시 전체 리프레시 세션 무효화는 [비밀번호 변경 API](../api/member-password-change.md)에 정리했습니다.
실제 탈퇴 API·개인정보 정리·탈퇴 시 세션 폐기 연결·체험 차단은 후속 작업입니다.
본 문서의 탈퇴 결과는 DB에 이미 탈퇴 상태가 저장된 회원의 접근 차단을 뜻합니다.
운영 브라우저 연동·동일 사이트 배포와 액세스 토큰 저장 정책은 별도 검증 대상으로 남깁니다.

## 5. 실제 검증 결과

관련 기존 테스트 12개 클래스를 함께 실행했습니다. 본 문서는 그중 인증 관련 6개 클래스의 결과를 정리합니다.
회원 동시성 결과는 [동시성 문서](../concurrency.md)에 기록하며 일부 검증 클래스가 겹칩니다.

| 검증 클래스 | 테스트 | 실패 | 오류 | 건너뜀 |
| --- | --- | --- | --- | --- |
| [JwtTokenProviderTest](../../src/test/java/com/recipick/global/security/JwtTokenProviderTest.java) | 38 | 0 | 0 | 0 |
| [AuthenticationWebTest](../../src/test/java/com/recipick/global/security/AuthenticationWebTest.java) | 17 | 0 | 0 | 0 |
| [MemberLoginIntegrationTest](../../src/test/java/com/recipick/domain/member/service/MemberLoginIntegrationTest.java) | 10 | 0 | 0 | 0 |
| [AdminLoginIntegrationTest](../../src/test/java/com/recipick/domain/member/service/AdminLoginIntegrationTest.java) | 13 | 0 | 0 | 0 |
| [TokenManagementIntegrationTest](../../src/test/java/com/recipick/domain/member/service/TokenManagementIntegrationTest.java) | 23 | 0 | 0 | 0 |
| [MemberWithdrawalFoundationIntegrationTest](../../src/test/java/com/recipick/domain/member/service/MemberWithdrawalFoundationIntegrationTest.java) | 6 | 0 | 0 | 0 |
| 합계 | 107 | 0 | 0 | 0 |

실행 시나리오에서 다음 결과를 확인했습니다.

| 시나리오 | 실제 결과·근거 |
| --- | --- |
| 만료·잘못된 서명·필수 클레임·주체 형식·토큰 용도 | JwtTokenProviderTest·AuthenticationWebTest의 기대 오류 검증 통과 |
| 회원·관리자 경로 분리와 슈퍼 관리자 전용 접근 | AuthenticationWebTest의 권한 거절 검증 통과 |
| 없는 계정·잘못된 비밀번호·비활성 관리자 | 회원·관리자 로그인 통합 검증 통과 |
| 동시 로그인 실패 6건 | 회원·관리자 각각 401 2건, 나머지 403, 실패 횟수 3 및 잠금 확인 |
| 같은 리프레시 토큰으로 동시 재발급 6건 | 200 1건, 나머지 AUTH-011, 성공한 새 토큰으로 재발급 가능 |
| 재발급과 로그아웃 동시 요청 | 최종 Redis 세션 없음, 회전 성공 시 받은 토큰도 재사용 거절 |
| 탈퇴 상태 저장 후 기존 액세스·리프레시·정상 비밀번호 사용 | 접근·재발급·로그인 거절, DB 조회·잠금 진입점도 거절 |
| 허용·비허용 Origin preflight | 인증 없이 허용된 Origin만 통과하는 MockMvc 검증 완료 |

위 결과는 자동 테스트 결과입니다. 실제 배포 브라우저·실 PG·부하·침투 테스트의 결과를 뜻하지 않습니다.
원본 리포트는 테스트 실행 후 `build/reports/tests/test/index.html`, XML은 `build/test-results/test/`에서 확인합니다.

### 재현 명령

```bash
./gradlew test \
  --tests 'com.recipick.global.security.JwtTokenProviderTest' \
  --tests 'com.recipick.global.security.AuthenticationWebTest' \
  --tests 'com.recipick.domain.member.service.MemberLoginIntegrationTest' \
  --tests 'com.recipick.domain.member.service.AdminLoginIntegrationTest' \
  --tests 'com.recipick.domain.member.service.TokenManagementIntegrationTest' \
  --tests 'com.recipick.domain.member.service.MemberWithdrawalFoundationIntegrationTest'
```

Docker가 켜져 있어야 하며 테스트 전용 MySQL·Redis는 IntegrationTestSupport가 실행합니다.
위 명령은 이번 통합 실행의 인증 부분만 재현합니다. 전체 실행 목록과 합계는 동시성 문서에 있습니다.

## 6. 관련 코드·규칙

- [SecurityConfig](../../src/main/java/com/recipick/global/config/SecurityConfig.java), [JwtTokenProvider](../../src/main/java/com/recipick/global/security/JwtTokenProvider.java), [인증 필터](../../src/main/java/com/recipick/global/security/JwtAuthenticationFilter.java).
- [회원 상태 검사](../../src/main/java/com/recipick/domain/member/service/MemberStatusService.java), [리프레시 세션](../../src/main/java/com/recipick/infra/redis/RedisRefreshTokenStore.java), [로그인 실패 기록](../../src/main/java/com/recipick/infra/redis/RedisLoginAttemptStore.java).
- [요구사항](../requirement.md), [인증·인가 컨벤션](../convention.md#인증인가), [공통 오류 코드](../convention.md#auth).
- [회원 동시성 근거](../concurrency.md), [아키텍처](layered-architecture.md), [목차](../README.md).
