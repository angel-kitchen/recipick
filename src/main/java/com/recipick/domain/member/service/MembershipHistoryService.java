package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.request.MembershipHistoryRequest;
import com.recipick.domain.member.dto.response.MembershipHistoryResponse;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipHistoryService {
    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final AdminAuditRecorder auditRecorder;
    private final Clock clock;

    // 개인정보 관련 조회도 감사 기록과 같은 쓰기 트랜잭션에서 처리한다.
    @Transactional
    public PageResponse<MembershipHistoryResponse> search(Long memberId, MembershipHistoryRequest request) {
        Member member = members.findById(memberId).filter(value -> !value.isWithdrawn())
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        var page = memberships.findAllByMemberId(memberId, request.toPageable())
                .map(membership -> MembershipHistoryResponse.from(membership, now));
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.MEMBER_MEMBERSHIP_HISTORY_VIEW,
                memberId, member.getMartId(), Map.of()));
        return PageResponse.from(page);
    }
}
