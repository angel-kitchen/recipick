package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.response.MemberMeResponse.MembershipSummary;
import com.recipick.domain.member.repository.MembershipRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipService {

    private final MembershipRepository membershipRepository;

    public MembershipSummary getSummary(Long memberId, LocalDateTime now) {
        return membershipRepository.findActiveMemberships(memberId, now, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(membership -> new MembershipSummary(true, membership.getType().name(), membership.getEndAt()))
                .orElseGet(() -> new MembershipSummary(false, null, null));
    }

    public boolean isTrialEligible(Long memberId) {
        return !membershipRepository.existsByMemberId(memberId);
    }
}
