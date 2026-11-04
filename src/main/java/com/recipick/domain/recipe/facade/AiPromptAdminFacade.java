package com.recipick.domain.recipe.facade;

import com.recipick.domain.recipe.dto.AiPromptActiveChange;
import com.recipick.domain.recipe.dto.request.AiPromptCreateRequest;
import com.recipick.domain.recipe.dto.response.AiPromptActiveResponse;
import com.recipick.domain.recipe.dto.response.AiPromptAdminResponse;
import com.recipick.domain.recipe.dto.response.AiPromptVersionResponse;
import com.recipick.domain.recipe.entity.AiPrompt;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.domain.recipe.service.AiPromptService;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditField;
import com.recipick.global.audit.AdminAuditRecorder;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 프롬프트 변경과 감사 기록을 한 트랜잭션에 묶어 기록에 실패하면 변경도 되돌린다.
// 지시문·메모는 자유 입력 원문이라 감사 로그에는 종류와 버전 번호만 남긴다
@Component
@RequiredArgsConstructor
public class AiPromptAdminFacade {

    private final AiPromptService aiPromptService;
    private final AdminAuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public AiPromptAdminResponse findPrompts(AiPromptType type) {
        return AiPromptAdminResponse.of(type, aiPromptService.findVersions(type));
    }

    @Transactional
    public AiPromptVersionResponse create(AiPromptCreateRequest request, Long adminId) {
        AiPrompt prompt = aiPromptService.create(request.type(), request.content(), request.memo(), adminId);
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.AI_PROMPT_CREATE, prompt.getId(), null, Map.of(
            AdminAuditField.TYPE, new AdminAuditChange(null, prompt.getPromptType()),
            AdminAuditField.VERSION, new AdminAuditChange(null, prompt.getVersion()))));
        return AiPromptVersionResponse.from(prompt);
    }

    @Transactional
    public AiPromptActiveResponse activate(Long promptId) {
        AiPromptActiveChange change = aiPromptService.activate(promptId);
        recordActiveChange(change);
        return new AiPromptActiveResponse(change.type(), change.afterVersion());
    }

    @Transactional
    public AiPromptActiveResponse reset(AiPromptType type) {
        aiPromptService.reset(type).ifPresent(this::recordActiveChange);
        return new AiPromptActiveResponse(type, null);
    }

    // 이미 활성인 버전을 다시 켜는 요청은 바뀐 것이 없어 감사 대상이 아니다
    private void recordActiveChange(AiPromptActiveChange change) {
        if (!change.changed()) {
            return;
        }
        AdminAuditChange version = new AdminAuditChange(change.beforeVersion(), change.afterVersion());
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.AI_PROMPT_ACTIVE_CHANGE, change.targetPromptId(),
            null, Map.of(AdminAuditField.VERSION, version)));
    }
}
