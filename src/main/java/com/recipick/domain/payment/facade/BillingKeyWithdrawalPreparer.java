package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.member.service.WithdrawalPaymentMethodPreparation;
import com.recipick.domain.member.service.WithdrawalPaymentMethodPreparation.Status;
import com.recipick.domain.member.service.WithdrawalPaymentMethodPreparer;
import com.recipick.domain.payment.config.BillingKeyDeletionProperties;
import com.recipick.domain.payment.exception.BillingKeyDeletionErrorCode;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.BillingKeyCleanupQueryRepository;
import com.recipick.domain.payment.repository.BillingKeyDeletionRepository;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BillingKeyWithdrawalPreparer implements WithdrawalPaymentMethodPreparer {
    private final BillingKeyDeletionProperties properties;
    private final MembershipBillingService memberships;
    private final MembershipPaymentQueryService payments;
    private final BillingKeyCleanupQueryRepository keys;
    private final BillingKeyDeletionRepository deletions;
    private final BillingKeyDeletionRequestProcessor requests;

    @Override
    public boolean isEnabled() {
        return properties.withdrawalReady();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public WithdrawalPaymentMethodPreparation prepare(Long memberId) {
        if (!isEnabled()) {
            throw new IllegalStateException("결제수단 정리가 비활성 상태입니다");
        }
        if (payments.existsUnresolved(memberships.findMembershipIds(memberId))) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_UNSETTLED_FOR_WITHDRAWAL);
        }
        for (Long keyId : keys.findIdsOfMember(memberId)) {
            requests.requestForWithdrawal(memberId, keyId);
        }
        if (deletions.hasReviewForMember(memberId)) {
            return new WithdrawalPaymentMethodPreparation(Status.REVIEW_REQUIRED,
                BillingKeyDeletionErrorCode.CLEANUP_REVIEW_REQUIRED);
        }
        if (keys.hasNotConfirmedDeleted(memberId)) {
            return new WithdrawalPaymentMethodPreparation(Status.PENDING, BillingKeyDeletionErrorCode.CLEANUP_PENDING);
        }
        return WithdrawalPaymentMethodPreparation.ready();
    }
}
