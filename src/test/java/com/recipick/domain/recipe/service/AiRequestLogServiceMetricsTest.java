package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.recipe.entity.AiRequestType;
import com.recipick.domain.recipe.entity.AiResultStatus;
import com.recipick.domain.recipe.repository.AiRequestLogRepository;
import com.recipick.infra.ai.dto.AiResult;
import com.recipick.infra.ai.dto.AiUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AiRequestLogServiceMetricsTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9505L;
    private static final AiResult<List<String>> RESULT =
        AiResult.success(List.of(), new AiUsage("fake", 0, 0, 0, 0));

    @Autowired
    AiRequestLogService aiRequestLogService;
    @Autowired
    AiRequestLogRepository aiRequestLogRepository;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        aiRequestLogRepository.deleteAll(aiRequestLogRepository.findAll().stream()
            .filter(log -> log.getMemberId().equals(MEMBER_ID))
            .toList());
    }

    private double noMatchCount() {
        Counter counter = meterRegistry.get("recipick.ai.requests")
            .tags("type", "RECOMMENDATION", "status", "NO_MATCH", "failure", "NONE")
            .counter();
        return counter.count();
    }

    @Test
    @DisplayName("이력이 커밋되면 지표를 한 번 기록한다")
    void save_recordsMetricsAfterCommit() {
        // given
        double before = noMatchCount();

        // when
        aiRequestLogService.save(MEMBER_ID, AiRequestType.RECOMMENDATION, "찌개", AiResultStatus.NO_MATCH,
            RESULT, List.of(), null);

        // then
        assertThat(noMatchCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("이력을 저장한 바깥 트랜잭션이 롤백되면 지표도 남기지 않는다")
    void save_skipsMetricsWhenOuterTransactionRollsBack() {
        // given
        double before = noMatchCount();

        // when
        Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
            aiRequestLogService.save(MEMBER_ID, AiRequestType.RECOMMENDATION, "찌개", AiResultStatus.NO_MATCH,
                RESULT, List.of(), null);
            throw new IllegalStateException("미리보기 저장 실패");
        }));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(noMatchCount()).isEqualTo(before);
        assertThat(aiRequestLogRepository.findAll()).noneMatch(log -> log.getMemberId().equals(MEMBER_ID));
    }
}
