package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.response.MembershipGrantResponse;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.member.service.MembershipGrantService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MembershipGrantFacade {
    private final MemberService members;
    private final MembershipBillingService billing;
    private final MembershipGrantService grants;
    private final Clock clock;

    @Transactional
    public MembershipGrantResponse grant(Long memberId, Long adminId) {
        // 회원 잠금이 첫 DB 작업이며 검증·지급·감사 기록을 같은 트랜잭션으로 묶는다.
        Member member = members.getMemberForUpdate(memberId);
        Admin admin = grants.getGrantAdmin(adminId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (billing.existsBlockingMembership(memberId, now)) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
        }
        return grants.grant(member, admin, now);
    }
}
