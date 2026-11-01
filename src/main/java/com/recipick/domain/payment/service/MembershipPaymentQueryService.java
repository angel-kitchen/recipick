package com.recipick.domain.payment.service;

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

// 회원 탈퇴가 "이 회원의 멤버십 결제 중 미정산이 있나"를 묻는 계약
// 잠그지 않는 일반 조회다. 그래서 다음이 전제다 — 1. 호출자 TX의 첫 DB 작업이 회원 잠금이고 잠금 전에 일반 조회가 없다,
// 2. 이 회원의 회차·시도·환불을 만들거나 바꾸는 모든 TX가 회원 잠금을 먼저 잡는다(4·5단계 설계 규칙),
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
            || membershipPaymentAttemptRepository.existsPaidRequiringRefundWithoutCompletedSystemRefund(membershipIds);
    }
}
