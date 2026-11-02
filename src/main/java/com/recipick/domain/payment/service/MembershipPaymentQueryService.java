package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 회원 탈퇴가 "이 회원의 멤버십 결제 중 미정산이 있나"를 묻는 계약. 다섯 조건 — ① 미해결 회차 ② 미해결 시도 ③ 환불 REQUESTED
// ④ 환불 미완료 PAID ⑤ 강한 정합성 차단 표시가 있는 시도(상태가 CANCELED·PAID·FAILED여도 운영 확인 전에는 정산 완료가 아니다)
// 잠그지 않는 일반 조회다. 그래서 다음이 전제다 — 1. 호출자 TX의 첫 DB 작업이 회원 잠금이고 잠금 전에 일반 조회가 없다,
// 2. 이 회원의 회차·시도·환불을 만들거나 바꾸는 모든 TX가 회원 잠금을 먼저 잡는다,
// 3. 잠금 대상은 member 행 하나다. 전제가 성립하면 잠금 뒤 첫 일반 읽기의 스냅숏은 최신이고, 틀릴 수 있는 방향은
// "SYSTEM 환불 COMPLETED가 늦게 보여 더 막는 쪽"뿐이다.
// MANDATORY는 바깥 트랜잭션의 존재만 강제한다. MEMBER 선행 잠금과 잠금 전 일반 조회 금지(1)는 호출자 계약이며,
// 호출 경로(탈퇴 Facade)와 MembershipPaymentQueryConcurrencyTest로 검증한다
@Service
@RequiredArgsConstructor
public class MembershipPaymentQueryService {

    private static final Set<MembershipPaymentStatus> UNRESOLVED_PAYMENT = Set.of(
        MembershipPaymentStatus.PENDING,
        MembershipPaymentStatus.RETRY_WAITING,
        MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED,
        MembershipPaymentStatus.UNKNOWN,
        MembershipPaymentStatus.MANUAL_REVIEW);
    private static final Set<MembershipPaymentAttemptStatus> UNRESOLVED_ATTEMPT = Set.of(
        MembershipPaymentAttemptStatus.READY,
        MembershipPaymentAttemptStatus.DISPATCHING,
        MembershipPaymentAttemptStatus.UNKNOWN,
        MembershipPaymentAttemptStatus.MANUAL_REVIEW);

    private final MembershipPaymentRepository membershipPaymentRepository;
    private final MembershipPaymentAttemptRepository membershipPaymentAttemptRepository;
    private final MembershipRefundRepository membershipRefundRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean existsUnresolved(Collection<Long> membershipIds) {
        if (membershipIds.isEmpty()) {
            return false;
        }
        return membershipPaymentRepository.existsByMembershipIdInAndStatusIn(membershipIds, UNRESOLVED_PAYMENT)
            || membershipPaymentAttemptRepository.existsByMembershipPaymentMembershipIdInAndStatusIn(
            membershipIds, UNRESOLVED_ATTEMPT)
            || membershipRefundRepository.existsByMembershipPaymentMembershipIdInAndStatus(
            membershipIds, MembershipRefundStatus.REQUESTED)
            || membershipPaymentAttemptRepository.existsPaidRequiringRefundWithoutCompletedSystemRefund(membershipIds)
            || membershipPaymentAttemptRepository.existsByMembershipPaymentMembershipIdInAndOpsReviewReasonIn(
            membershipIds, MembershipOpsReviewReason.BLOCKING);
    }

    // 같은 갱신 회차에 다음 시도를 만들어도 되는지. existsUnresolved(C-17)와 같은 다섯 조건을 보되 ① 회차 조건에서만
    // "현재 회차가 RETRY_WAITING·카드 대기인 그 한 행"을 뺀다 — 자기 대기 회차 때문에 영원히 막히지 않게.
    // 현재 회차의 미해결 시도·환불 REQUESTED·환불 미완료 PAID는 그대로 막는다. 전제는 existsUnresolved와 같다
    // (회원 잠금 뒤 첫 일반 조회). 호출자는 현재 회차를 PK로 잠갔고 상태 변경을 flush한 뒤 부른다
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean existsUnresolvedForRetry(Collection<Long> membershipIds, Long currentPaymentId) {
        if (membershipIds.isEmpty()) {
            return false;
        }
        return membershipPaymentRepository.existsUnresolvedExceptOwnWaiting(membershipIds, UNRESOLVED_PAYMENT,
            currentPaymentId)
            || membershipPaymentAttemptRepository.existsByMembershipPaymentMembershipIdInAndStatusIn(
            membershipIds, UNRESOLVED_ATTEMPT)
            || membershipRefundRepository.existsByMembershipPaymentMembershipIdInAndStatus(
            membershipIds, MembershipRefundStatus.REQUESTED)
            || membershipPaymentAttemptRepository.existsPaidRequiringRefundWithoutCompletedSystemRefund(membershipIds)
            || membershipPaymentAttemptRepository.existsByMembershipPaymentMembershipIdInAndOpsReviewReasonIn(
            membershipIds, MembershipOpsReviewReason.BLOCKING);
    }
}
