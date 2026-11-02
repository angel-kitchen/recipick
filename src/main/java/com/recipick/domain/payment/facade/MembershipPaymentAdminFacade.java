package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.dto.request.MembershipPaymentAttemptSearchRequest;
import com.recipick.domain.payment.dto.response.MembershipPaymentAttemptDetailResponse;
import com.recipick.domain.payment.dto.response.MembershipPaymentAttemptSummaryResponse;
import com.recipick.domain.payment.dto.response.MembershipPaymentRecheckResponse;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.domain.payment.service.MembershipReconciliationResult;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 멤버십 결제 시도 운영 조회·재조회(SUPER_ADMIN). 목록·상세는 조회와 감사 기록을 한 쓰기 TX에서 커밋한다(기록 실패면 결과를 주지 않는다).
// 재조회는 감사 접수 TX 커밋 → 같은 결제 ID GET(트랜잭션 밖) → TX2 → 최신 상태 응답 순이다. 차단 시도는 GET만 하고 반영하지 않는다.
// 운영자는 결제 ID·금액·상태를 입력하지 않는다 — 시도 ID로 이용권을 찾고 이용권으로 회원을 역조회한다. 표시 해제·강제 PAID는 없다
@Component
@RequiredArgsConstructor
public class MembershipPaymentAdminFacade {

    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipRefundRepository refunds;
    private final MembershipBillingService membershipBillingService;
    private final MembershipChargeExecutor executor;
    private final MembershipPaymentAdminAuditProcessor auditProcessor;
    private final AdminAuditRecorder adminAuditRecorder;
    private final MembershipBillingProperties properties;

    @Transactional
    public PageResponse<MembershipPaymentAttemptSummaryResponse> findAttempts(
        MembershipPaymentAttemptSearchRequest request) {
        PageResponse<MembershipPaymentAttemptSummaryResponse> response = PageResponse.from(attempts.searchForAdmin(
            request.toStatuses(), request.membershipId(), request.opsReviewOnly(), request.blockedOnly(),
            MembershipOpsReviewReason.BLOCKING, request.toPageable()).map(this::summary));
        adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.MEMBERSHIP_PAYMENT_LIST_VIEW, null, null,
            null));
        return response;
    }

    @Transactional
    public MembershipPaymentAttemptDetailResponse getAttempt(Long attemptId) {
        MembershipPaymentAttempt attempt = attempts.findById(attemptId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        MembershipPaymentAttemptDetailResponse response = MembershipPaymentAttemptDetailResponse.of(summary(attempt),
            attempt.getMembershipPayment(), refunds.findByMembershipPaymentAttemptIdOrderByIdAsc(attemptId));
        adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.MEMBERSHIP_PAYMENT_DETAIL_VIEW, attemptId,
            null, null));
        return response;
    }

    @Transactional(propagation = Propagation.NEVER)
    public MembershipPaymentRecheckResponse recheck(Long attemptId) {
        auditProcessor.recordRecheck(attemptId);
        MembershipPaymentAttemptRef ref = attempts.findRef(attemptId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
        MembershipReconciliationResult result = executor.recheckForAdmin(memberId, attemptId);
        MembershipPaymentAttempt after = attempts.findWithPaymentById(attemptId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        return MembershipPaymentRecheckResponse.of(summary(after), result.outcome(), result.verdict());
    }

    private MembershipPaymentAttemptSummaryResponse summary(MembershipPaymentAttempt attempt) {
        return MembershipPaymentAttemptSummaryResponse.of(attempt, properties.unknownWindow());
    }
}
