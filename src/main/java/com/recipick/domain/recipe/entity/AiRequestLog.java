package com.recipick.domain.recipe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ai_request_log",
        indexes = {
                @Index(name = "id_ai_request_log_created_at", columnList = "created_at"),
                @Index(name = "idx_ai_request_log_member_id_created_at", columnList = "member_id, created_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auto_cart_request_id")
    private AutoCartRequest autoCartRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AiRequestType requestType;

    @Column(columnDefinition = "text")
    private String rawText;

    @Column(columnDefinition = "json")
    private String parsedResult;

    @Column(columnDefinition = "text")
    private String matchedRecipeIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AiResultStatus resultStatus;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, length = 50)
    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer cacheReadTokens;

    @Column(nullable = false)
    private int latencyMs;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 성공 여부를 따로 받지 않고 결과 상태로 정해, FALLBACK인데 성공으로 남는 이력이 생기지 않게 한다
    @Builder
    private AiRequestLog(
            Long memberId,
            AutoCartRequest autoCartRequest,
            AiRequestType requestType,
            String rawText,
            String parsedResult,
            String matchedRecipeIds,
            AiResultStatus resultStatus,
            String failureReason,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            Integer cacheReadTokens,
            int latencyMs
    ) {
        this.memberId = memberId;
        this.autoCartRequest = autoCartRequest;
        this.requestType = requestType;
        this.rawText = rawText;
        this.parsedResult = parsedResult;
        this.matchedRecipeIds = matchedRecipeIds;
        this.resultStatus = resultStatus;
        this.success = resultStatus != AiResultStatus.FALLBACK;
        this.failureReason = failureReason;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.latencyMs = latencyMs;
    }
}
