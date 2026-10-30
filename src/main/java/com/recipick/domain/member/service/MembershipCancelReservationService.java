package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.response.MembershipCancelReservationResponse;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipCancelReservationService {
    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final Clock clock;

    @Transactional
    public MembershipCancelReservationResponse reserve(Long memberId, Long membershipId) {
        // 회원 잠금이 첫 DB 작업이다. 탈퇴·지급과 같은 회원을 기준으로 직렬화한다.
        members.findByIdForUpdate(memberId).filter(member -> !member.isWithdrawn())
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        Membership membership = memberships.findByIdAndMemberIdForUpdate(membershipId, memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        membership.reserveCancellation(now);
        return MembershipCancelReservationResponse.from(membership, now);
    }
}
