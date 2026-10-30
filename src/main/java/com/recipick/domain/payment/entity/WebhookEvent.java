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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// PortOne 웹훅 inbox. 요청 스레드는 검증 → 저장까지만 하고, 반영은 지연 큐 Worker가 이 행을 잡아서 한다.
// 같은 웹훅이 다시 와도 webhook_id UNIQUE로 한 번만 저장되고, 처리는 상태로 한 번만 끝난다
@Getter
@Entity
@Table(
    name = "webhook_event",
    uniqueConstraints = @UniqueConstraint(
        name = WebhookEvent.WEBHOOK_ID_UNIQUE_CONSTRAINT, columnNames = "webhook_id"),
    // 탈퇴 정산이 결제별 미해결 웹훅을 잠금 읽기(FOR SHARE)로 본다. 인덱스가 없으면 전체 스캔이 되어 테이블 전 행이 잠긴다
    indexes = @Index(
        name = "idx_webhook_event_portone_payment_id_status", columnList = "portone_payment_id, status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent extends BaseTimeEntity {

    // 수신 Facade가 UNIQUE 충돌을 다른 제약 위반과 구분하는 데 쓴다. 조회 없이 "이미 받은 웹훅"으로 판정한다
    public static final String WEBHOOK_ID_UNIQUE_CONSTRAINT = "uk_webhook_event_webhook_id";

    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UNIQUE 제약이 가리키는 컬럼이라 이름을 명시해 둔다. 필드명 변경이 제약과 어긋나지 않게
    @Column(name = "webhook_id")
    private String webhookId;

    // PortOne 웹훅 이벤트 이름을 그대로 저장한다
    @Column(length = 50)
    private String type;

    private String portonePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private WebhookEventStatus status;

    // 서명 검증을 통과한 원문 JSON. 헤더·시크릿은 저장하지 않는다
    @Column(columnDefinition = "text")
    private String payload;

    // Worker가 잡은 횟수. 최대 시도 판단은 Worker가 한다
    private int attemptCount;

    // 무시·재시도·수동 확인 사유. 처리에 성공하면 비운다
    @Column(length = 500)
    private String failureReason;

    private LocalDateTime finishedAt;

    private WebhookEvent(
        String webhookId,
        String type,
        String portonePaymentId,
        String payload,
        WebhookEventStatus status
    ) {
        this.webhookId = webhookId;
        this.type = type;
        this.portonePaymentId = portonePaymentId;
        this.payload = payload;
        this.status = status;
    }

    public static WebhookEvent received(String webhookId, String type, String portonePaymentId, String payload) {
        return new WebhookEvent(webhookId, type, portonePaymentId, payload, WebhookEventStatus.RECEIVED);
    }

    // 처리 대상이 아닌 웹훅도 저장한다. 같은 webhook_id 재전송을 UNIQUE로 막고 200을 돌려줘야 PortOne 재전송이 멈춘다
    public static WebhookEvent ignored(
        String webhookId,
        String type,
        String portonePaymentId,
        String payload,
        String reason,
        LocalDateTime now
    ) {
        WebhookEvent event = new WebhookEvent(webhookId, type, portonePaymentId, payload, WebhookEventStatus.IGNORED);
        event.failureReason = truncate(reason);
        event.finishedAt = Objects.requireNonNull(now, "now는 필수입니다.");
        return event;
    }

    // Worker가 처리 시작 전에 부른다. 끝난 이벤트와 다른 Worker가 처리 중인 이벤트는 건너뛴다(false).
    // 큐에 같은 이벤트가 두 번 있어도, Fallback이 재등록해도 반영은 한 번만 일어난다.
    // PROCESSING인데 updated_at이 오래됐으면 Worker가 죽어 끝내지 못한 행으로 보고 다시 잡는다
    public boolean claim(LocalDateTime now, Duration processingTimeout) {
        if (this.status.isFinished()) {
            return false;
        }
        if (this.status == WebhookEventStatus.PROCESSING && !isStale(now, processingTimeout)) {
            return false;
        }
        this.status = WebhookEventStatus.PROCESSING;
        this.attemptCount++;
        return true;
    }

    public void markProcessed(LocalDateTime now) {
        requireProcessing();
        this.status = WebhookEventStatus.PROCESSED;
        this.failureReason = null;
        this.finishedAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }

    public void markIgnored(String reason, LocalDateTime now) {
        requireProcessing();
        this.status = WebhookEventStatus.IGNORED;
        this.failureReason = truncate(reason);
        this.finishedAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }

    // 다음 실행 시각은 큐(ZSET score)가 갖는다. 여기서는 상태와 사유만 남긴다
    public void markRetryPending(String reason) {
        requireProcessing();
        this.status = WebhookEventStatus.RETRY_PENDING;
        this.failureReason = truncate(reason);
    }

    public void markManualReview(String reason, LocalDateTime now) {
        requireProcessing();
        this.status = WebhookEventStatus.MANUAL_REVIEW;
        this.failureReason = truncate(reason);
        this.finishedAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }

    // updated_at은 Auditing이 채우므로 처리 중 마지막으로 상태를 바꾼 시각이다. 아직 한 번도 저장되지 않았으면 오래된 것으로 본다
    private boolean isStale(LocalDateTime now, Duration processingTimeout) {
        LocalDateTime updatedAt = getUpdatedAt();
        return updatedAt == null || !updatedAt.plus(processingTimeout).isAfter(now);
    }

    // 잡지 않은 이벤트의 결과를 쓰면 다른 Worker의 처리와 섞인다. 호출 순서 버그라 업무 오류가 아닌 내부 오류로 던진다
    private void requireProcessing() {
        if (this.status != WebhookEventStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 상태에서만 결과를 기록할 수 있습니다. 현재: " + this.status);
        }
    }

    // 사유는 PortOne 오류 본문이 그대로 들어올 수 있어 컬럼 길이에 맞춰 자른다
    private static String truncate(String reason) {
        Objects.requireNonNull(reason, "reason은 필수입니다.");
        return reason.length() <= FAILURE_REASON_MAX_LENGTH ? reason : reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
