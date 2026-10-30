package com.recipick.domain.recipe.facade;

import com.recipick.domain.recipe.dto.request.AiRequestLogAdminSearchRequest;
import com.recipick.domain.recipe.dto.response.AiRequestLogAdminResponse;
import com.recipick.domain.recipe.service.AiRequestLogService;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 회원이 입력한 원문을 보는 조회라 감사 기록 대상이다. AdminAuditRecorder가 쓰기 가능한 트랜잭션을 요구하므로 readOnly를 쓰지 않고,
// 조회와 기록을 한 트랜잭션으로 묶어 기록이 실패하면 조회 결과도 응답하지 않는다
@Component
@RequiredArgsConstructor
public class AiRequestLogAdminFacade {

    private final AiRequestLogService aiRequestLogService;
    private final AdminAuditRecorder adminAuditRecorder;

    @Transactional
    public PageResponse<AiRequestLogAdminResponse> findAiRequests(AiRequestLogAdminSearchRequest request) {
        PageResponse<AiRequestLogAdminResponse> response = PageResponse.from(
            aiRequestLogService.searchForAdmin(request).map(AiRequestLogAdminResponse::from));

        // 결과가 0건이어도 실제 관리자 조회이므로 기록한다. 검색 조건과 원문은 감사 로그에 복사하지 않는다
        adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.AI_REQUEST_LIST_VIEW, null, null, null));
        return response;
    }
}
