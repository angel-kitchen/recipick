package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

import com.recipick.domain.payment.dto.response.PointBalanceResponse;
import com.recipick.domain.payment.dto.response.PointHistoryResponse;
import com.recipick.domain.payment.service.PointQueryService;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.response.PageResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PointAdminQueryFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private PointQueryService pointQueryService;

    @Mock
    private AdminAuditRecorder adminAuditRecorder;

    @Captor
    private ArgumentCaptor<AdminAuditCommand> commandCaptor;

    @InjectMocks
    private PointAdminQueryFacade pointAdminQueryFacade;

    @Test
    @DisplayName("회원 포인트를 조회한 뒤 대상 회원을 담아 POINT_BALANCE_VIEW를 한 번 기록한다")
    void getBalance() {
        // given
        PointBalanceResponse balance = new PointBalanceResponse(MEMBER_ID, 500L, 500L, LocalDate.of(2027, 1, 1));
        given(pointQueryService.getBalance(MEMBER_ID)).willReturn(balance);

        // when
        PointBalanceResponse response = pointAdminQueryFacade.getBalance(MEMBER_ID);

        // then
        assertThat(response).isEqualTo(balance);
        InOrder inOrder = inOrder(pointQueryService, adminAuditRecorder);
        inOrder.verify(pointQueryService).getBalance(MEMBER_ID);
        inOrder.verify(adminAuditRecorder).record(commandCaptor.capture());
        assertThat(commandCaptor.getValue().action()).isEqualTo(AdminAuditAction.POINT_BALANCE_VIEW);
        assertThat(commandCaptor.getValue().targetId()).isEqualTo(MEMBER_ID);
        assertThat(commandCaptor.getValue().martId()).isNull();
        assertThat(commandCaptor.getValue().changes()).isEmpty();
    }

    @Test
    @DisplayName("이력이 0건이어도 실제 관리자 조회이므로 POINT_HISTORY_VIEW를 기록한다")
    void findHistoryRecordsEvenWhenEmpty() {
        // given
        PageResponse<PointHistoryResponse> empty = new PageResponse<>(List.of(), 0, 20, 0, 0);
        given(pointQueryService.findHistory(MEMBER_ID, null, PAGE)).willReturn(empty);

        // when
        PageResponse<PointHistoryResponse> response = pointAdminQueryFacade.findHistory(MEMBER_ID, null, PAGE);

        // then
        assertThat(response).isEqualTo(empty);
        InOrder inOrder = inOrder(pointQueryService, adminAuditRecorder);
        inOrder.verify(pointQueryService).findHistory(MEMBER_ID, null, PAGE);
        inOrder.verify(adminAuditRecorder).record(commandCaptor.capture());
        assertThat(commandCaptor.getValue().action()).isEqualTo(AdminAuditAction.POINT_HISTORY_VIEW);
        assertThat(commandCaptor.getValue().targetId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("감사 기록이 실패하면 예외를 그대로 던져 조회 결과를 응답하지 않는다")
    void auditFailureBlocksResponse() {
        // given
        given(pointQueryService.getBalance(MEMBER_ID)).willReturn(new PointBalanceResponse(MEMBER_ID, 0L, 0L, null));
        willThrow(new BusinessException(CommonErrorCode.INVALID_INPUT)).given(adminAuditRecorder).record(any());

        // when, then
        assertThatThrownBy(() -> pointAdminQueryFacade.getBalance(MEMBER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }
}
