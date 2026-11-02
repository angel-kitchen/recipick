package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.MembershipPaymentSettlementChecker;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 탈퇴 정산 MEMBERSHIP_PAYMENT 영역. 탈퇴 트랜잭션과 같은 순서(회원 잠금 → 판정)로 부른다
class MembershipPaymentSettlementCheckerIntegrationTest extends MembershipRenewalFlowSupport {

    @Autowired MembershipPaymentSettlementChecker checker;
    @Autowired MemberService memberService;

    @Test
    @DisplayName("처리 중인 갱신 시도가 있으면 탈퇴를 BILLING-004로 거절한다")
    void rejectsWithdrawalWhileUnsettled() {
        // given
        Long membershipId = activeMembership();
        renewalProcessor.accept(member.getId(), membershipId);

        // when
        Throwable thrown = requireSettled();

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
            .isEqualTo(PaymentErrorCode.MEMBERSHIP_PAYMENT_UNSETTLED_FOR_WITHDRAWAL);
    }

    @Test
    @DisplayName("다른 조건이 모두 정산됐어도 강한 차단 시도가 남아 있으면 탈퇴를 거절한다(시도·회차가 끝난 상태여도)")
    void rejectsWithdrawalWhileBlockedAttemptRemains() {
        // given
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        PORTONE.getPaymentPaid(attempt(attemptId).getPortonePaymentId(), FEE, "KRW", T.minusMinutes(1));
        executor.reconcile(member.getId(), attemptId, MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
        jdbc.update("UPDATE membership_payment_attempt SET status = 'CANCELED', failure_reason = 'NOT_DISPATCHED', "
            + "completed_at = ? WHERE id = ?", T, attemptId);
        time.paymentStatus(onlyRenewalPayment(membershipId).getId(), MembershipPaymentStatus.FAILED);
        assertThat(attempt(attemptId).getOpsReviewReason()).isEqualTo(MembershipOpsReviewReason.UNDISPATCHED_PAID);

        // when
        Throwable thrown = requireSettled();

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
            .isEqualTo(PaymentErrorCode.MEMBERSHIP_PAYMENT_UNSETTLED_FOR_WITHDRAWAL);
    }

    @Test
    @DisplayName("멤버십 결제가 모두 정산됐으면 통과한다")
    void allowsWhenSettled() {
        // given
        activeMembership();

        // when
        Throwable thrown = requireSettled();

        // then
        assertThat(thrown).isNull();
    }

    private Throwable requireSettled() {
        return catchThrowable(() -> transactionTemplate.executeWithoutResult(s -> {
            memberService.lockActiveMember(member.getId());
            checker.requireSettled(member.getId(), 0L);
        }));
    }
}
