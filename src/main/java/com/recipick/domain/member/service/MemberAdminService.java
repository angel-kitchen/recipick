package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.MemberAdminRow;
import com.recipick.domain.member.dto.MemberMembershipData;
import com.recipick.domain.member.dto.request.MemberAdminSearchRequest;
import com.recipick.domain.member.dto.response.MemberAdminResponse;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.response.PageResponse;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditAction;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAdminService {

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final Clock clock;
    private final AdminAuditRecorder auditRecorder;

    @Transactional
    public PageResponse<MemberAdminResponse> findMembers(MemberAdminSearchRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<MemberAdminRow> page = memberRepository.searchMembers(request.email(), request.name(), request.membershipActive(),
                request.membershipType(), request.membershipSource(), now, request.toPageable());
        List<Long> ids = page.getContent().stream().map(MemberAdminRow::memberId).toList();
        Map<Long, MemberMembershipData> active = ids.isEmpty() ? Map.of()
                : membershipRepository.findActiveMembershipsForMembers(ids, now).stream()
                        .collect(Collectors.toMap(MemberMembershipData::memberId, Function.identity(),
                                (first, ignored) -> first));
        Set<Long> trialUsed = ids.isEmpty() ? Set.of()
                : Set.copyOf(membershipRepository.findMemberIdsWithTypeHistory(ids, MembershipType.TRIAL));
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.MEMBER_LIST_VIEW, null, null, Map.of()));
        return PageResponse.from(page.map(row -> {
            MemberMembershipData membership = active.get(row.memberId());
            return new MemberAdminResponse(row.memberId(), row.email(), row.name(), row.martId(), membership != null,
                    membership == null ? null : membership.type().name(),
                    membership == null ? null : membership.endAt(),
                    trialUsed.contains(row.memberId()), row.createdAt());
        }));
    }
}
