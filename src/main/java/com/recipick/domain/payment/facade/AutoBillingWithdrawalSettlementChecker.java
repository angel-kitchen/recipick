package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.payment.config.BillingKeyDeletionProperties;
import com.recipick.domain.payment.exception.BillingKeyDeletionErrorCode;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.BillingKeyCleanupQueryRepository;
import com.recipick.domain.payment.repository.BillingKeyDeletionRepository;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.global.exception.BusinessException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 검사만 한다. 회원 잠금 아래 신규 카드 등록·신규 청구와 직렬화되며 PG 호출은 절대 없다.
@Component
@Order(1000)
@RequiredArgsConstructor
public class AutoBillingWithdrawalSettlementChecker implements WithdrawalSettlementChecker {
    private final BillingKeyDeletionProperties properties;
    private final BillingKeyCleanupQueryRepository keys;
    private final BillingKeyDeletionRepository deletions;
    private final MembershipBillingService memberships;
    private final MembershipPaymentQueryService payments;

    @Override
    public Set<Scope> supportedScopes() {
        return properties.withdrawalReady() ? Set.of(Scope.AUTO_BILLING) : Set.of();
    }

    // 스위치가 꺼져 있으면 이 Checker는 영역을 선언하지 않으므로(위) 탈퇴 Processor의 영역 누락 검사가 MEMBER-008로 막는다.
    // 여기서 또 던지면 다른 Checker가 AUTO_BILLING을 맡는 구성(테스트 스텁)까지 막으므로 판정만 건너뛴다
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireSettled(Long memberId, Long acknowledgedPointBalance) {
        if (!properties.withdrawalReady()) {
            return;
        }
        if (payments.existsUnresolved(memberships.findMembershipIds(memberId))) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_UNSETTLED_FOR_WITHDRAWAL);
        }
        if (deletions.hasReviewForMember(memberId)) {
            throw new BusinessException(BillingKeyDeletionErrorCode.CLEANUP_REVIEW_REQUIRED);
        }
        if (keys.hasNotConfirmedDeleted(memberId)) {
            throw new BusinessException(BillingKeyDeletionErrorCode.CLEANUP_PENDING);
        }
    }
}
