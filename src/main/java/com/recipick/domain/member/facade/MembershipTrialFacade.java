package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.request.MembershipTrialStartRequest;
import com.recipick.domain.member.dto.response.MembershipTrialStartResponse;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.member.service.MembershipTrialService;
import com.recipick.domain.payment.service.BillingKeyService;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MembershipTrialFacade {
    private final MemberService members;
    private final MembershipBillingService billing;
    private final MembershipTrialService trials;
    private final BillingKeyService billingKeys;
    private final MembershipPaymentQueryService payments;
    private final Clock clock;

    @Transactional
    public MembershipTrialStartResponse start(Long memberId, MembershipTrialStartRequest request) {
        // 카드·정산 조회보다 먼저 회원을 잠가 구독·지급·탈퇴와 직렬화한다.
        Member member = members.getMemberForUpdate(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        var consent = request.toConsent(now);
        trials.validateStart(member, consent, now);
        if (!billingKeys.hasActiveBillingKey(memberId)) {
            throw new BusinessException(MemberErrorCode.PAYMENT_METHOD_REQUIRED);
        }
        if (payments.existsUnresolved(billing.findMembershipIds(memberId))) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_PAYMENT_IN_PROGRESS);
        }
        return trials.start(member, consent, now);
    }
}
