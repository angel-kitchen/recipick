package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.dto.request.MembershipHistoryRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class MembershipHistoryServiceTest {
    @Mock MemberRepository members;
    @Mock MembershipRepository memberships;
    @Mock AdminAuditRecorder recorder;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-29T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final MembershipHistoryRequest request = new MembershipHistoryRequest(null, null);
    MembershipHistoryService service;

    @BeforeEach
    void setup() {
        service = new MembershipHistoryService(members, memberships, recorder, clock);
    }

    @Test
    @DisplayName("감사 저장 실패는 조회 성공으로 반환하지 않고 호출자에게 전달한다")
    void propagatesAuditFailure() {
        // given
        given(members.findById(1L)).willReturn(Optional.of(Member.builder().build()));
        given(memberships.findAllByMemberId(1L, request.toPageable()))
                .willReturn(new PageImpl<>(List.of(), request.toPageable(), 0));
        doThrow(new IllegalStateException("감사 저장 실패")).when(recorder).record(any());
        // when, then
        assertThatThrownBy(() -> service.search(1L, request)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("회원이 없으면 이력과 감사 기록을 조회하지 않는다")
    void refusesMissingMember() {
        // given
        given(members.findById(1L)).willReturn(Optional.empty());
        // when, then
        assertThatThrownBy(() -> service.search(1L, request)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(memberships, recorder);
    }

    @Test
    @DisplayName("이용 시작은 포함하고 종료는 제외하며 과거 이력을 읽어도 상태를 변경하지 않는다")
    void usesOneClockAndExistingStatusBoundaries() {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        Membership startsNow = Membership.builder().type(MembershipType.TRIAL).source(MembershipSource.TRIAL)
                .startAt(now).endAt(now.plusDays(7)).build();
        Membership endsNow = Membership.builder().type(MembershipType.PAID).source(MembershipSource.SUBSCRIBE)
                .startAt(now.minusMonths(1)).endAt(now).autoRenew(true).build();
        given(members.findById(1L)).willReturn(Optional.of(Member.builder().build()));
        given(memberships.findAllByMemberId(1L, request.toPageable()))
                .willReturn(new PageImpl<>(List.of(startsNow, endsNow), request.toPageable(), 2));
        // when
        var result = service.search(1L, request);
        // then
        assertThat(result.content()).extracting(value -> value.status()).containsExactly("ACTIVE", "EXPIRED");
        assertThat(endsNow.isAutoRenew()).isTrue();
        assertThat(endsNow.getTerminatedAt()).isNull();
        ArgumentCaptor<AdminAuditCommand> command = ArgumentCaptor.forClass(AdminAuditCommand.class);
        verify(recorder).record(command.capture());
        assertThat(command.getValue().action()).isEqualTo(AdminAuditAction.MEMBER_MEMBERSHIP_HISTORY_VIEW);
        assertThat(command.getValue().targetId()).isEqualTo(1L);
        assertThat(command.getValue().martId()).isNull();
    }
}
