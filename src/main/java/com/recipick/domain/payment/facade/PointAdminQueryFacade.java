package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.dto.response.PointBalanceResponse;
import com.recipick.domain.payment.dto.response.PointHistoryResponse;
import com.recipick.domain.payment.service.PointQueryService;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 관리자 포인트 조회의 진입점. 감사 기록은 쓰기 가능한 트랜잭션을 요구하므로 readOnly를 쓰지 않는다.
// 조회와 감사 기록이 한 트랜잭션이라 기록이 실패하면 조회 결과도 응답하지 않는다.
@Component
@RequiredArgsConstructor
public class PointAdminQueryFacade {

    private final PointQueryService pointQueryService;
    private final AdminAuditRecorder adminAuditRecorder;

    @Transactional
    public PointBalanceResponse getBalance(Long memberId) {
        PointBalanceResponse response = pointQueryService.getBalance(memberId);
        adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.POINT_BALANCE_VIEW, memberId, null, null));
        return response;
    }

    @Transactional
    public PageResponse<PointHistoryResponse> findHistory(Long memberId, Long orderId, Pageable pageable) {
        PageResponse<PointHistoryResponse> response = pointQueryService.findHistory(memberId, orderId, pageable);
        adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.POINT_HISTORY_VIEW, memberId, null, null));
        return response;
    }

}
