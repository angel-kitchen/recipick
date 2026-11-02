# 멤버십 즉시 해지 API

`POST /api/memberships/{membershipId}/terminate`

회원 액세스 토큰과 `Idempotency-Key` 헤더 필수. 요청 본문은 없다. 이용권 ID는 양수이며, 회원 ID는 인증 정보에서만 얻는다.

회원 Facade는 `Propagation.NEVER`로 결제 C-5 `MembershipRefundFacade.requestMemberRefund(memberId, membershipId, idempotencyKey)`를 호출한다. 헤더 값은 회원 쪽에서 변환하지 않는다. 접수 커밋 → PG 호출 → 결과 반영은 결제 도메인 소유이며, 환불 완료와 이용권 종료를 같은 트랜잭션에서 처리한다. 회원 쪽은 별도로 종료·환불 계산·상태 변경을 하지 않는다.

HTTP 200의 `data`는 결제 `MembershipRefundResult`를 그대로 반환한다.

| 필드 | 의미 |
| --- | --- |
| membershipId, membershipPaymentId, refundId | 이용권·결제 회차·환불 ID |
| refundStatus | COMPLETED·REQUESTED·FAILED |
| paidAmount, periodDays, usedDays | 결제금액·결제기간 일수·이용일수 |
| usedDayFee, penaltyAmount, refundAmount | 이용요금·위약금·환불금액(서버 계산) |
| requestedAt, completedAt | 요청·완료 시각. 한국 시간 +09:00, 미완료면 completedAt은 null |
| membershipTerminated | 환불 완료와 이용권 종료까지 끝났으면 true(0원 환불 포함) |
| reviewRequired | true면 운영 확인 안내 |

- COMPLETED: 환불 완료·이용권 종료. 내 멤버십을 다시 조회한다.
- REQUESTED: 환불 확인 중. 별도로 종료 완료로 표시하거나 새 키로 재요청하지 않는다.
- FAILED: 명확한 실패. 이 환불 요청으로 이용권이 종료되지 않았으며 새 키로 다시 요청할 수 있다. 현재 혜택은 내 멤버십 조회로 확인한다.
- reviewRequired=true: 운영 확인이 필요함을 함께 안내한다.

미완료 이용권의 혜택은 기존 이용기간·유예 정책에 따른다. REQUESTED·FAILED가 혜택 기간을 연장하는 것은 아니다.

| HTTP | 오류 코드 | 조건 |
| --- | --- | --- |
| 400 | COMMON-007 | 멱등 키 누락·빈 값 |
| 400 | COMMON-001 | 키 64자 초과·내부 공백 또는 이용권 ID 검증 실패 |
| 409 | COMMON-011 | 같은 키로 다른 이용권 요청 |
| 404 | MEMBER-001 | 신규 접수에서 탈퇴 회원 발견(토큰 인증 단계 거절은 공통 인증 계약) |
| 404 | MEMBERSHIP-006 | 없는 이용권 또는 다른 회원 소유 |
| 409 | MEMBERSHIP-008 | 즉시 해지 대상 아님. 다른 키의 이미 환불 완료 이용권 요청 포함 |
| 409 | MEMBERSHIP-010 | 결제 확인 중 또는 다른 환불 진행 중 |
| 409 | BILLING-021·BILLING-022 | 환불 정합성 문제로 접수 거절 |

멱등 키 정규화·검증·회원별 구분은 결제에서 처리한다. 같은 키 재요청은 새 환불·재계산·PG 호출 없이 같은 환불의 현재 상태를 반환한다. 최초 REQUESTED 응답이 이후 COMPLETED로 바뀔 수 있다. 같은 키로 완료 결과를 다시 조회하는 것은 허용한다.

신규 환경 변수·스키마 변경은 없다. 실제 PG 동작·운영 검증은 결제 담당의 [멤버십 환불 계약](membership-refund.md)을 따른다. 이 API는 상품 주문의 관리자 환불 기능과 별개다.
