# 회원 비밀번호 변경

- `PATCH /api/members/me/password`, MEMBER 인증 필수
- 요청: `currentPassword`, `newPassword` 문자열. 새 비밀번호는 가입과 같은 8~20자·영문·숫자·특수문자 포함·공백 불가
- 성공: `204 No Content`, 본문 없음. 현재 브라우저의 memberRefreshToken Cookie도 만료
- 입력 오류: `400 COMMON-001`, 현재 비밀번호 불일치: `400 MEMBER-005`
- 미인증: `401 AUTH-001`, 탈퇴·없는 회원 토큰: `401 AUTH-011`, 관리자 토큰: `403 AUTH-004`
- 회원 잠금 시간 초과: `409 COMMON-010`

회원 행을 잠근 뒤 현재 비밀번호를 확인하고 새 해시·리프레시 세션 버전을 함께 커밋합니다.
재발급 시 JWT의 버전과 DB 버전을 비교하므로 모든 기기의 이전 리프레시 토큰이 거절됩니다.
2026-11-11부터 기존 액세스도 tokenVersion 비교로 즉시 거절합니다. 새 비밀번호로 로그인하면 새 버전의 세션을 발급합니다.
이전 보안 스키마의 토큰은 재로그인을 요구합니다. 관리자 세션은 현재 관리자 상태·권한을 검사합니다.

Redis 세션 목록 조회·정리는 DB 회원 잠금 밖에서 처리합니다. 재인증 실패 카운터는 비밀번호 검증 시 Redis에 별도로 남기며 DB 롤백으로 지우지 않습니다. 변경 전에 확보한 세션 키만 커밋 후 삭제해
커밋 이후 새 비밀번호로 로그인한 세션을 지우지 않습니다. 동시 요청으로 늦게 저장된 이전 버전 세션도 재발급할 수 없습니다.
커밋 후 Redis 삭제 실패는 경고로 남기고 성공 응답을 유지합니다. DB 버전으로 이미 무효화됐고 남은 기록은 14일 TTL 내 만료됩니다.
변경 전 세션 목록 조회에 실패하면 DB 변경을 시작하지 않습니다.

비밀번호 변경·탈퇴 재인증은 회원별 10분 내 연속 5회 실패 시 10분 제한하며 MEMBER-009(403)을 반환합니다. 새 비밀번호가 기존과 같은 경우도 요구사항의 규칙을 만족하면 허용합니다.
요청 DTO·로그에 비밀번호 원문을 출력하지 않습니다.

## 기존 DB 반영

컬럼이 없는 기존 테이블에 한 번 적용할 DDL입니다. 로컬 JPA update로 이미 생성된 환경은 중복 적용하지 않습니다.

```sql
ALTER TABLE member
    ADD COLUMN refresh_token_version BIGINT NOT NULL DEFAULT 0;
```

기존 회원과 버전 없는 기존 리프레시 토큰은 버전 0으로 유지합니다. 비밀번호를 변경하면 버전이 증가합니다.

## 과거 검증 결과(2026-10-27, 당시 액세스 유지 정책 기준)

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

## 현재 정책

[인증·세션 보완](auth-session-hardening.md)에 새 액세스 차단·절대 만료·CSRF·Cookie 분리를 설명합니다. 위 과거 테스트 수치는 이번 실행 결과가 아닙니다.
