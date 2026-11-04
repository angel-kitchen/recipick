package com.recipick.domain.recipe.entity;

import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 저장한 지시문은 고치지 않고 새 버전으로 쌓아, 이전 버전으로 되돌리거나 이력의 결과를 버전별로 비교할 수 있게 한다
@Getter
@Entity
@Table(name = "ai_prompt",
    uniqueConstraints = @UniqueConstraint(name = "uk_ai_prompt_type_version", columnNames = {"prompt_type", "version"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiPrompt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AiPromptType promptType;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 200)
    private String memo;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Long createdByAdminId;

    @Builder
    private AiPrompt(AiPromptType promptType, int version, String content, String memo, Long createdByAdminId) {
        this.promptType = promptType;
        this.version = version;
        this.content = content;
        this.memo = memo;
        this.active = false;
        this.createdByAdminId = createdByAdminId;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
