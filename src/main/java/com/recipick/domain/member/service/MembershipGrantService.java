package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.response.MembershipGrantResponse;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipGrantService {

    private static final int GRANT_DAYS = 30;

    private final MemberService memberService;
    private final AdminRepository adminRepository;
    private final MembershipRepository membershipRepository;
    private final Clock clock;

    @Transactional
    public MembershipGrantResponse grant(Long memberId, Long adminId) {
        // 다른 조회보다 먼저 회원을 잠가 대기 중 생성된 이용권도 검사할 수 있게 한다.
        Member member = memberService.getMemberForUpdate(memberId);
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ACCESS_DENIED));
        if (!admin.isActive()) {
            throw new BusinessException(AuthErrorCode.INACTIVE_ADMIN);
        }
        if (admin.getRole() != AdminRole.SUPER_ADMIN) {
            throw new BusinessException(AuthErrorCode.SUPER_ADMIN_ONLY);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!membershipRepository.findActiveMemberships(memberId, now, PageRequest.of(0, 1)).isEmpty()) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
        }
        Membership membership = membershipRepository.save(Membership.builder()
                .member(member).grantedByAdmin(admin).type(MembershipType.PAID).source(MembershipSource.ADMIN_GRANT)
                .startAt(now).endAt(now.plusDays(GRANT_DAYS)).autoRenew(false).build());
        return new MembershipGrantResponse(membership.getId(), memberId, membership.getType().name(),
                membership.getSource().name(), membership.getStartAt(), membership.getEndAt(), admin.getId());
    }
}
