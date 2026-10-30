package com.recipick.domain.recipe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.domain.recipe.dto.request.AiRequestLogAdminSearchRequest;
import com.recipick.domain.recipe.entity.AiRequestLog;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.repository.AiRequestLogRepository;
import com.recipick.domain.recipe.repository.AutoCartRequestRepository;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRequestLogService {

    private final AiRequestLogRepository aiRequestLogRepository;
    private final AutoCartRequestRepository autoCartRequestRepository;
    private final ObjectMapper objectMapper;
    private final AiRequestMetrics aiRequestMetrics;

    @Transactional
    public void save(Long memberId, AiRequestType requestType, String rawText, AiResultStatus resultStatus,
                     AiResult<?> aiResult, List<Long> matchedRecipeIds, Long autoCartRequestId) {
        AiUsage usage = aiResult.usage();
        aiRequestLogRepository.save(AiRequestLog.builder()
            .memberId(memberId)
            .autoCartRequest(autoCartRequestId == null
                ? null
                : autoCartRequestRepository.getReferenceById(autoCartRequestId))
            .requestType(requestType)
            .rawText(rawText)
            .parsedResult(toJson(aiResult.content()))
            .matchedRecipeIds(matchedRecipeIds.isEmpty() ? null : matchedRecipeIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")))
            .resultStatus(resultStatus)
            .failureReason(aiResult.isSuccess() ? null : aiResult.failureType().name())
            .model(usage.model())
            .inputTokens(usage.inputTokens())
            .outputTokens(usage.outputTokens())
            .cacheReadTokens(usage.cacheReadTokens())
            .latencyMs(Math.toIntExact(usage.latencyMs()))
            .build());

        // 자연어 장보기는 미리보기 저장과 같은 트랜잭션에서 이력을 남기므로 저장 뒤에 롤백될 수 있다.
        // 이력에 없는 요청이 지표에만 잡히지 않도록 커밋에 성공한 뒤에 기록한다
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aiRequestMetrics.record(requestType, resultStatus, aiResult);
            }
        });
    }

    public Page<AiRequestLog> searchForAdmin(AiRequestLogAdminSearchRequest request) {
        return aiRequestLogRepository.searchForAdmin(
            request.toRequestType(),
            request.success(),
            request.toFromAt(),
            request.toToExclusive(),
            request.toPageable());
    }

    private String toJson(Object content) {
        if (content == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 분석 결과를 JSON으로 바꿀 수 없습니다.", e);
        }
    }
}
