# 카드(빌링키) 등록

PortOne V2 + KG이니시스 테스트 채널의 카드 빌링키를 회원에게 등록합니다. 카드번호·CVC는 저장하지 않고 빌링키만 저장합니다.
3차 2단계(PR-2a) 기준이며, 실제 청구는 4단계, 삭제는 6단계에서 열립니다.

## API

- `POST /api/billing-keys/issue-requests`, MEMBER 인증 필수, 본문 없음
    - 성공 `200`, `data`: `issueId`(일회성 토큰, 30분), `issueName`, `storeId`, `channelKey`, `customerId`, `displayAmount`(3900), `currency`(KRW), `expiresAt`
    - 응답 헤더 `Cache-Control: no-store`. 프론트는 이 값을 SDK `requestIssueBillingKey`에 그대로 넣습니다
    - 기능 미개방: `503 BILLING-008`, 탈퇴·없는 회원: `404 MEMBER-001`, PortOne 상점·채널 설정 없음: `500 COMMON-008`
- `POST /api/billing-keys`, MEMBER 인증 필수
    - 요청: `issueId`(발급 요청이 준 토큰, 64자 이하), `billingKey`(SDK 발급 결과, 255자 이하)
    - 성공 `200`, `data`: `billingKeyId`, `status`(`ACTIVE`), `outcome`(`REGISTERED` 새 등록 / `REPLACED` 이전 카드 교체 / `ALREADY_ACTIVE` 같은 키 재요청 / `REACTIVATED` 교체됐던 내 키 되살림)
    - 입력 오류: `400 COMMON-001`
    - PortOne에 없음·삭제됨·상점·채널·소유·수단 불일치·실채널(LIVE): `400 BILLING-001`
    - 다른 회원이 등록한 키: `409 BILLING-002`
    - 내 키지만 REVOKED·DELETE_REQUESTED·DELETED: `409 BILLING-006`
    - 발급 요청 없음·타인·만료·다른 키에 사용됨·만료된 증빙으로 교체된 키 되살리기: `409 BILLING-007`
    - 기능 미개방: `503 BILLING-008`, PortOne 오류 응답·연결 실패·설정 없음: `500 COMMON-008`, PortOne 타임아웃: `500 COMMON-009`
    - 회원 잠금 시간 초과: `409 COMMON-010`
- `GET /api/billing-keys/me`, MEMBER 인증 필수
    - `200`, `data`: `registered`, `billingKeyId`, `status`, `issuedAt`(미등록이면 `registered: false`, 나머지 `null`)
    - 기능 플래그와 무관하게 동작합니다. 빌링키 원문·카드 정보는 어떤 응답에도 없습니다

## 흐름

1. 프론트가 `POST /api/billing-keys/issue-requests`로 토큰·설정을 받고, `GET /api/members/me`의 이름·휴대전화·이메일과 함께
   SDK `requestIssueBillingKey`를 호출합니다(`issueId`에 토큰 그대로, `customer.customerId`에 `customerId` 그대로).
2. 발급이 끝나면 `POST /api/billing-keys`에 `issueId`와 `billingKey`를 보냅니다.
3. 서버는 트랜잭션 밖에서 PortOne `GET /billing-keys/{billingKey}`를 조회해 다음을 모두 확인합니다 — `ISSUED`, 키 일치, 상점 일치,
   **우리 채널 키와 같은 채널 객체의 `type`이 `TEST`**, PortOne이 돌려준 `issueId`가 서버가 발급한 토큰과 같음(소유의 1차 증거),
   `customer.id`가 있으면 로그인 회원과 같음, 카드 수단.
4. 회원 행을 잠근 트랜잭션에서 등록·교체를 판정하고 발급 요청을 그 키에 묶습니다(USED). 같은 회원·같은 키 재요청은 멱등이고,
   교체된 키를 되살리는 요청만 발급 뒤 30분 안에서 허용합니다.

## 운영 차단

- 2단계 코드는 **테스트 채널(TEST) 빌링키만 등록**합니다. 운영 PortOne 설정이 실채널이면 어떤 설정으로도 등록이 성공하지 않습니다
  (`BillingKeyIssuanceVerifier.ALLOWED_CHANNEL_TYPE`). 실채널 허용은 빌링키 암호화·키 관리를 적용하는 PR에서 코드로만 엽니다.
- 기능 플래그 `recipick.billing-key.registration-enabled`(환경변수 `BILLING_KEY_REGISTRATION_ENABLED`, 운영 기본 false)는
  보조 스위치입니다. 꺼져 있으면 등록 API 두 개가 `503 BILLING-008`입니다.
- 운영 등록 활성화 조건(모두 충족 + 검수 GO 뒤에만)
    1. 빌링키 암호화·키 관리 설계 검수·적용, 같은 PR에서 허용 채널 유형을 검증된 설정으로 전환
    2. 테스트 채널 실연동 E-1(SDK 발급)·E-2(조회 응답에 우리 채널 `type == TEST`, `issueId` 그대로, `customer.id`) 확인
    3. 아래 실연동 절차 R-1 통과, 미검증 항목에 코드 리뷰 확인 기록
    4. 운영 DB에서 `billing_key` 원문 컬럼을 읽을 수 있는 계정 최소화, 운영 로깅에 `org.springframework.web.client` DEBUG 없음
       (빌링키가 요청 URL 경로에 들어갑니다)
    5. 기존 DB 반영(아래) 완료

## 실연동 절차 R-1 (로컬 앱, 플래그 true, KG이니시스 테스트 채널)

결과는 "통과/실패·필드 존재 여부"만 적고 **빌링키·토큰·API Secret 원문은 어디에도 적지 않습니다.**

1. `POST /api/billing-keys/issue-requests` → 200, 토큰 수신
2. 브라우저 SDK 발급 → 성공
3. `POST /api/billing-keys` → 200 `REGISTERED`
4. 같은 요청 재전송 → 200 `ALREADY_ACTIVE`
5. 새 발급으로 다른 카드 → 200 `REPLACED`, 이전 키 `REPLACED`
6. 임의 문자열 키 → 400 `BILLING-001`
7. 토큰을 바꿔 전송 → 409 `BILLING-007`
8. 잘못된 API Secret으로 1회 → 500 `COMMON-008`, ERROR 로그 1줄
9. 앱 로그를 키·토큰 문자열로 grep → 0건

## 자동 테스트로 재현하지 못한 상황(미검증)

| 상황 | 자동 테스트 | R-1 | 관리 |
| --- | --- | --- | --- |
| PortOne 404 → BILLING-001 | 없음 | 6번 | R-1 |
| PortOne 401·403 → COMMON-008 + ERROR | 없음 | 8번 | R-1 |
| PortOne 400·5xx·본문 해석 실패 → COMMON-008 | 역직렬화(미지 status → UNKNOWN)만 | 재현 불가 | 미검증, 코드 리뷰로 확인 |
| PortOne 타임아웃 → COMMON-009 / 타임아웃이 아닌 연결 실패 → COMMON-008, 둘 다 DB 무변경 | 없음 | 재현 불가 | 미검증, 코드 리뷰로 확인(Facade에 트랜잭션 없음) |
| 실채널(LIVE) 빌링키 거절 | 검증기 단위 테스트 | 실채널 없어 재현 불가 | 단위 테스트 + E-2 |
| 실제 KG이니시스 응답의 `issueId`·`customer.id` | 없음 | E-2 | 활성화 조건 2 |

## 기존 DB 반영

운영은 `ddl-auto: update`지만 아래 DDL을 먼저 적용하고, 앱 기동 뒤 Hibernate가 추가로 바꾼 것이 없는지 확인합니다.
절차·되돌리기 문장·확인 SQL은 `docs/deploy-implementation.md` 10절을 따릅니다. 지우는 문장은 없습니다.

```sql
ALTER TABLE billing_key
  ADD COLUMN delete_requested_at DATETIME(6) NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE billing_key
  ADD INDEX idx_billing_key_member_id_status (member_id, status);
-- 기존 UNIQUE 인덱스(Hibernate 생성 이름)를 엔티티의 이름으로 바꾼다. 인덱스 내용은 그대로이고 메타데이터만 바뀐다
-- SELECT INDEX_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_key' AND COLUMN_NAME = 'portone_billing_key' AND NON_UNIQUE = 0;
ALTER TABLE billing_key RENAME INDEX <조회한 이름> TO uk_billing_key_portone_billing_key;

CREATE TABLE billing_key_issue_request (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  member_id      BIGINT       NOT NULL,
  issue_id       VARCHAR(64)  NOT NULL,
  status         VARCHAR(20)  NOT NULL,
  expires_at     DATETIME(6)  NOT NULL,
  used_at        DATETIME(6)  NULL,
  billing_key_id BIGINT       NULL,
  created_at     DATETIME(6)  NULL,
  updated_at     DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_billing_key_issue_request_issue_id (issue_id),
  CONSTRAINT fk_billing_key_issue_request_billing_key
    FOREIGN KEY (billing_key_id) REFERENCES billing_key (id)
) ENGINE = InnoDB;
```

## 보안 메모

- 빌링키 원문은 응답·로그·`toString`·예외 cause에 넣지 않습니다.
- UNIQUE 충돌: MySQL은 위반 메시지에 중복 값(키 원문)을 넣습니다(`Duplicate entry '<값>' for key '<테이블>.<제약>'`). Logback TurboFilter
  `BillingKeyLogMaskingFilter`가 출력 전에 판정합니다 — 메시지의 `for key` 뒤 제약 식별자를 꺼내(테이블 접두어·따옴표·대소문자 정규화)
  `uk_billing_key_portone_billing_key`·`uk_billing_key_issue_request_issue_id`와 **정확히 같을 때만** 막습니다.
  ① Hibernate `SqlExceptionHelper`가 예외 변환 전에 남기는 메시지 줄은 그 로거에 한해 막고, ② 예외 객체를 함께 넘기는 줄
  (`GlobalExceptionHandler` 등)은 로거와 관계없이 cause·suppressed 체인에 두 제약 위반이 있으면 막은 뒤 원문 없는 진단 한 줄
  (`…BillingKeyLogMasking` 로거: 원래 로거·예외 클래스·제약 이름)을 남깁니다. 오류 코드·SQLState 줄(`SQL Error: 1062, SQLState: 23000`),
  다른 제약의 충돌(중복 값에 같은 글자가 들어 있어도), 그 밖의 SQL 오류는 그대로 남습니다.
- 필터를 달 수 없는 환경(Logback이 아님)이면 애플리케이션이 기동하지 않습니다. 원문 차단이 카드 등록의 필수 조건이기 때문입니다.
- 실제 충돌을 유도한 테스트(`BillingKeyRegistrationConcurrencyTest.uniqueConflictLeavesNoKeyInLogs`)가 로그 메시지와 예외의 중첩
  cause까지 원문이 없음을 확인합니다.
- 빌링키 UNIQUE 위반만 `BILLING-002`로 바꿉니다(제약 이름 `uk_billing_key_portone_billing_key`). 다른 무결성 오류는 그대로 올립니다.
- 토큰 `issueId`는 발급 응답에만 실리고 `Cache-Control: no-store`로 내려갑니다. 요청·응답 DTO의 `toString`은 가려집니다.
