# 회원 비밀번호 변경

- `PATCH /api/members/me/password`, MEMBER 인증 필수
- 요청: `currentPassword`, `newPassword` 문자열. 새 비밀번호는 가입과 같은 8~20자·영문·숫자·특수문자 포함·공백 불가
- 성공: `204 No Content`, 본문 없음. 현재 브라우저의 refreshToken 쿠키도 만료
- 입력 오류: `400 COMMON-001`, 현재 비밀번호 불일치: `400 MEMBER-005`
- 미인증: `401 AUTH-001`, 탈퇴·없는 회원 토큰: `401 AUTH-011`, 관리자 토큰: `403 AUTH-004`
- 회원 잠금 시간 초과: `409 COMMON-010`

회원 행을 잠근 뒤 현재 비밀번호를 확인하고 새 해시·리프레시 세션 버전을 함께 커밋합니다.
재발급 시 JWT의 버전과 DB 버전을 비교하므로 모든 기기의 이전 리프레시 토큰이 거절됩니다.
기존 액세스 토큰은 만료까지 사용 가능하며 새 비밀번호로 로그인하면 새 세션을 발급합니다.
버전이 없는 기존 리프레시 토큰은 0으로 해석하고 DB 버전이 0일 때만 허용합니다. 관리자 세션에는 이 회원 버전 정책을 적용하지 않습니다.

Redis 네트워크 호출은 DB 회원 잠금 밖에서 처리합니다. 변경 전에 확보한 세션 키만 커밋 후 삭제해
커밋 이후 새 비밀번호로 로그인한 세션을 지우지 않습니다. 동시 요청으로 늦게 저장된 이전 버전 세션도 재발급할 수 없습니다.
커밋 후 Redis 삭제 실패는 경고로 남기고 성공 응답을 유지합니다. DB 버전으로 이미 무효화됐고 남은 기록은 14일 TTL 내 만료됩니다.
변경 전 세션 목록 조회에 실패하면 DB 변경을 시작하지 않습니다.

현재 비밀번호 재입력 실패에 별도 횟수 잠금은 추가하지 않습니다. 새 비밀번호가 기존과 같은 경우도 요구사항의 규칙을 만족하면 허용합니다.
요청 DTO·로그에 비밀번호 원문을 출력하지 않습니다.

## 기존 DB 반영

컬럼이 없는 기존 테이블에 한 번 적용할 DDL입니다. 로컬 JPA update로 이미 생성된 환경은 중복 적용하지 않습니다.

```sql
ALTER TABLE member
    ADD COLUMN refresh_token_version BIGINT NOT NULL DEFAULT 0;
```

기존 회원과 버전 없는 기존 리프레시 토큰은 버전 0으로 유지합니다. 비밀번호를 변경하면 버전이 증가합니다.

## 검증 결과(2026-10-27)

- 전체 회귀 테스트: 1,551개 통과(마지막 재발급 경합 단위 테스트 보강 전).
- 보강 후 관련 테스트 4개 클래스·67개 통과: 비밀번호 API 23개, Facade 실패 처리 2개, 토큰 관리 3개, JWT 39개.
- 다중 세션 폐기·기존 액세스 유지·새 로그인 보존·늦은 이전 세션 저장·동시 변경·DB 롤백·Redis 장애·계정 격리를 검증했습니다.
- 실제 MySQL·Redis와 MockMvc를 사용했으며, 실제 배포 브라우저 검증은 별도입니다.

```bash
./gradlew test \
  --tests 'com.recipick.domain.member.controller.MemberPasswordChangeIntegrationTest' \
  --tests 'com.recipick.domain.member.facade.MemberPasswordFacadeTest' \
  --tests 'com.recipick.domain.member.service.TokenManagementServiceTest' \
  --tests 'com.recipick.global.security.JwtTokenProviderTest'
```
