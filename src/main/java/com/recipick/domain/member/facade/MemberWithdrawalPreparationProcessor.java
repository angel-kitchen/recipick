package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.request.MemberWithdrawRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.member.service.WithdrawalPaymentMethodPreparation;
import com.recipick.domain.member.service.WithdrawalPaymentMethodPreparer;
import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.order.service.OrderService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberWithdrawalPreparationProcessor {
    private final MemberService members;
    private final OrderService orders;
    private final MembershipService memberships;
    private final ObjectProvider<WithdrawalSettlementChecker> settlementCheckers;
    private final WithdrawalPaymentMethodPreparer paymentMethods;
    private final com.recipick.domain.member.service.SensitivePasswordGuard passwordGuard;
    private final Clock clock;

    public boolean isEnabled() {
        return paymentMethods.isEnabled();
    }

    @Transactional
    public WithdrawalPaymentMethodPreparation prepare(Long memberId, MemberWithdrawRequest request) {
        // 회원을 먼저 잠근 뒤 재인증·기본 제한을 확인한다. 포인트·개인정보는 변경하지 않는다.
        Member member = members.getMemberForUpdate(memberId);
        passwordGuard.verify(memberId, request.password(), member.getPasswordHash());
        if (orders.existsInProgressOrder(memberId)) {
            throw new BusinessException(MemberErrorCode.WITHDRAW_BLOCKED_ORDER);
        }
        if (memberships.existsBlocking(memberId, LocalDateTime.now(clock))) {
            throw new BusinessException(MemberErrorCode.WITHDRAW_BLOCKED_MEMBERSHIP);
        }
        var covered = EnumSet.noneOf(WithdrawalSettlementChecker.Scope.class);
        settlementCheckers.orderedStream().forEach(checker -> {
            var scopes = checker.supportedScopes();
            if (scopes != null) {
                covered.addAll(scopes);
            }
        });
        if (!covered.containsAll(EnumSet.allOf(WithdrawalSettlementChecker.Scope.class))) {
            throw new BusinessException(MemberErrorCode.WITHDRAW_SETTLEMENT_NOT_READY);
        }
        // PENDING·REVIEW_REQUIRED도 예외 없이 반환해 카드 정리 접수를 커밋한다.
        return paymentMethods.prepare(memberId);
    }
}
