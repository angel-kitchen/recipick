package com.recipick.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "ai_request_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auto_cart_request_id")
    private AutoCartRequest autoCartRequest;

    @Column(columnDefinition = "text")
    private String rawText;

    @Column(columnDefinition = "json")
    private String parsedResult;

    @Column(columnDefinition = "text")
    private String matchedRecipeIds;

    private boolean success;

    @Column(length = 500)
    private String failureReason;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AiRequestLog(
            Long memberId,
            AutoCartRequest autoCartRequest,
            String rawText,
            String parsedResult,
            String matchedRecipeIds,
            boolean success,
            String failureReason
    ) {
        this.memberId = memberId;
        this.autoCartRequest = autoCartRequest;
        this.rawText = rawText;
        this.parsedResult = parsedResult;
        this.matchedRecipeIds = matchedRecipeIds;
        this.success = success;
        this.failureReason = failureReason;
    }
}
