package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.request.MemberWithdrawRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MemberWithdrawalService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.member.service.TrialBlockService;
import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.order.service.OrderService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberWithdrawalProcessor {
    private final MemberService members;
    private final OrderService orders;
    private final MembershipService memberships;
    private final TrialBlockService trialBlocks;
    private final MemberWithdrawalService withdrawal;
    private final ObjectProvider<WithdrawalSettlementChecker> settlementCheckers;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @Transactional
    public void withdraw(Long memberId, MemberWithdrawRequest request) {
        // 일반 조회보다 먼저 잠가 주문 생성·멤버십 지급과 직렬화한다.
        Member member = members.getMemberForUpdate(memberId);
        if (!encoder.matches(request.password(), member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (orders.existsInProgressOrder(memberId)) {
            throw new BusinessException(MemberErrorCode.WITHDRAW_BLOCKED_ORDER);
        }
        if (memberships.isActive(memberId)) {
            throw new BusinessException(MemberErrorCode.WITHDRAW_BLOCKED_MEMBERSHIP);
        }
        List<WithdrawalSettlementChecker> checkers = settlementCheckers.orderedStream().toList();
        Set<WithdrawalSettlementChecker.Scope> covered = EnumSet.noneOf(WithdrawalSettlementChecker.Scope.class);
        for (WithdrawalSettlementChecker checker : checkers) {
            Set<WithdrawalSettlementChecker.Scope> scopes = checker.supportedScopes();
            if (scopes != null) {
                covered.addAll(scopes);
            }
        }
        if (!covered.containsAll(EnumSet.allOf(WithdrawalSettlementChecker.Scope.class))) {
            throw new BusinessException(MemberErrorCode.WITHDRAW_SETTLEMENT_NOT_READY);
        }
        checkers.forEach(checker -> checker.requireSettled(memberId));
        LocalDateTime now = LocalDateTime.now(clock);
        trialBlocks.recordWithdrawal(memberId, now);
        withdrawal.withdraw(member, now);
    }
}
