# 무료체험 시작 API

`POST /api/memberships/trial` — 회원 액세스 토큰 필요. 요청 회원 본인에게만 적용한다.

```json
{
  "autopayAgreed": true,
  "consentedAmount": 3900,
  "consentTextVersion": "v1"
}
```

`consentTextVersion`은 화면에 표시한 동의 문구의 버전이며 공백 불가, 최대 20자다. 동의 시각은 서버에서 기록한다. 프론트는 월 3,900원, 체험 종료 후 첫 결제, 자동갱신 및 해지 방법을 안내하고 동의받아 요청한다.

성공은 HTTP 201이며 `data`에 다음 값을 반환한다.

```json
{
  "membershipId": 123,
  "type": "TRIAL",
  "source": "TRIAL",
  "startAt": "2026-11-02T12:00:00+09:00",
  "endAt": "2026-11-09T12:00:00+09:00",
  "autoRenew": true
}
```

시작 시 청구하지 않는다. 이용 기간은 서버에서 정한 시작 시각부터 정확히 7일이며, 활성화와 동의 시각도 같은 시각이다. 체험 종료 후 결제·전환은 결제 도메인의 별도 흐름에서 처리한다.

| HTTP | 오류 코드 | 조건 |
| --- | --- | --- |
| 400 | MEMBERSHIP-004 | 동의 누락, 금액 불일치, 문구 버전 누락·길이 초과 |
| 409 | MEMBERSHIP-002 | 활성·결제 대기·전환 대기 이용권 존재 |
| 409 | MEMBERSHIP-003 | 실제 활성화 이력 또는 휴대전화 체험 차단 존재 |
| 409 | MEMBERSHIP-005 | 활성 결제 카드 없음 |
| 409 | MEMBERSHIP-009 | 전체 이용권 중 미해결·미정산 결제 존재 |

미인증·탈퇴 회원의 토큰은 공통 인증 규칙에 따라 거절한다. 중복 요청은 새 이용권을 만들지 않고 MEMBERSHIP-002로 거절한다. 이 API는 Idempotency-Key를 사용하지 않는다.

`MembershipTrialFacade`의 한 쓰기 트랜잭션에서 첫 DB 작업으로 회원 행을 잠근다. 동의·체험 자격 → 활성 카드(C-10) → 전체 이용권 ID(C-8)의 미정산 확인(C-17) → 체험 생성을 수행한다. 다른 회원 서비스와 결제 서비스는 Facade에서 조립한다. 생성 서비스도 같은 시각으로 자격을 재검증하며 실패는 전체 롤백한다. 외부 PG 호출, 신규 환경 변수, 스키마 변경은 없다.

요구사항: [무료체험](../requirement.md). 미정산 판정: [C-17 계약](membership-payment-unresolved.md).
