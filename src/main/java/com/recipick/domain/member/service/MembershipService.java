package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.response.MemberMeResponse.MembershipSummary;
import com.recipick.domain.member.dto.response.MembershipMeResponse;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public MembershipMeResponse getMe(Long memberId) {
        validateMember(memberId);
        Optional<Membership> membership = findActive(memberId, LocalDateTime.now(clock));
        MembershipMeResponse.Current current = membership.map(value -> new MembershipMeResponse.Current(
                value.getId(), value.getType().name(), value.getSource().name(), value.getStartAt(), value.getEndAt(),
                value.isAutoRenew(), value.getBillingAnchorDate(), value.getCancelReservedAt())).orElse(null);
        return new MembershipMeResponse(current != null, current, isTrialEligible(memberId));
    }

    public boolean isActive(Long memberId) {
        validateMember(memberId);
        return findActive(memberId, LocalDateTime.now(clock)).isPresent();
    }

    public void requireActiveMembership(Long memberId) {
        if (!isActive(memberId)) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_REQUIRED);
        }
    }

    public Optional<MembershipType> getTypeAt(Long memberId, LocalDateTime at) {
        if (at == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        validateMember(memberId);
        return membershipRepository.findMembershipsAt(memberId, at, PageRequest.of(0, 1)).stream()
                .findFirst().map(Membership::getType);
    }

    public MembershipSummary getSummary(Long memberId, LocalDateTime now) {
        return findActive(memberId, now)
                .map(membership -> new MembershipSummary(true, membership.getType().name(), membership.getEndAt()))
                .orElseGet(() -> new MembershipSummary(false, null, null));
    }

    public boolean isTrialEligible(Long memberId) {
        return !membershipRepository.existsByMemberId(memberId);
    }

    private Optional<Membership> findActive(Long memberId, LocalDateTime now) {
        return membershipRepository.findActiveMemberships(memberId, now, PageRequest.of(0, 1)).stream().findFirst();
    }

    private void validateMember(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (!memberRepository.existsByIdAndWithdrawnAtIsNull(memberId)) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
    }
}
