package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "webhook_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String webhookId;

    // PortOne 웹훅 이벤트 이름을 그대로 저장한다
    @Column(length = 50)
    private String type;

    private String portonePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WebhookEventStatus status;

    @Column(columnDefinition = "text")
    private String payload;

    private int attemptCount;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime finishedAt;

    @Builder
    private WebhookEvent(
            String webhookId,
            String type,
            String portonePaymentId,
            WebhookEventStatus status,
            String payload
    ) {
        this.webhookId = webhookId;
        this.type = type;
        this.portonePaymentId = portonePaymentId;
        this.status = status;
        this.payload = payload;
    }
}
