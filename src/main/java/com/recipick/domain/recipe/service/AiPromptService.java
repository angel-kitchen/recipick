package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.ActiveAiPrompt;
import com.recipick.domain.recipe.dto.AiPromptActiveChange;
import com.recipick.domain.recipe.entity.AiPrompt;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.domain.recipe.exception.AiErrorCode;
import com.recipick.domain.recipe.repository.AiPromptRepository;
import com.recipick.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiPromptService {

    private final AiPromptRepository aiPromptRepository;

    public ActiveAiPrompt getActive(AiPromptType type) {
        return aiPromptRepository.findByPromptTypeAndActiveTrue(type)
            .map(prompt -> new ActiveAiPrompt(type, prompt.getVersion(), prompt.getContent()))
            .orElseGet(() -> new ActiveAiPrompt(type, null, type.defaultContent()));
    }

    public List<AiPrompt> findVersions(AiPromptType type) {
        return aiPromptRepository.findAllByPromptTypeOrderByVersionDesc(type);
    }

    @Transactional
    public AiPrompt create(AiPromptType type, String content, String memo, Long adminId) {
        int nextVersion = aiPromptRepository.findAllByPromptTypeForUpdate(type).stream()
            .mapToInt(AiPrompt::getVersion)
            .max()
            .orElse(0) + 1;
        try {
            return aiPromptRepository.saveAndFlush(AiPrompt.builder()
                .promptType(type)
                .version(nextVersion)
                .content(content)
                .memo(memo)
                .createdByAdminId(adminId)
                .build());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AiErrorCode.AI_PROMPT_VERSION_CONFLICT);
        }
    }

    @Transactional
    public AiPromptActiveChange activate(Long promptId) {
        AiPromptType type = aiPromptRepository.findById(promptId)
            .orElseThrow(() -> new BusinessException(AiErrorCode.AI_PROMPT_NOT_FOUND))
            .getPromptType();
        List<AiPrompt> versions = aiPromptRepository.findAllByPromptTypeForUpdate(type);
        Integer beforeVersion = activeVersion(versions);
        AiPrompt target = versions.stream()
            .filter(prompt -> prompt.getId().equals(promptId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(AiErrorCode.AI_PROMPT_NOT_FOUND));
        versions.forEach(prompt -> {
            if (prompt == target) {
                prompt.activate();
            } else {
                prompt.deactivate();
            }
        });
        return new AiPromptActiveChange(type, target.getId(), beforeVersion, target.getVersion());
    }

    // 되돌릴 활성 버전이 없으면 바뀐 것이 없어 감사 대상도 없다
    @Transactional
    public Optional<AiPromptActiveChange> reset(AiPromptType type) {
        List<AiPrompt> versions = aiPromptRepository.findAllByPromptTypeForUpdate(type);
        Optional<AiPrompt> active = versions.stream().filter(AiPrompt::isActive).findFirst();
        active.ifPresent(AiPrompt::deactivate);
        return active.map(prompt -> new AiPromptActiveChange(type, prompt.getId(), prompt.getVersion(), null));
    }

    private static Integer activeVersion(List<AiPrompt> versions) {
        return versions.stream()
            .filter(AiPrompt::isActive)
            .map(AiPrompt::getVersion)
            .findFirst()
            .orElse(null);
    }
}
