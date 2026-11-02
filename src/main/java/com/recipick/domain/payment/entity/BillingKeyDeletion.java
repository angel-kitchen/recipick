package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.BillingKeyDeletionReason;
import com.recipick.domain.payment.entity.enums.BillingKeyDeletionStatus;
import com.recipick.domain.payment.service.BillingKeyDeletionCode;
import com.recipick.domain.payment.service.BillingKeyDeletionPolicy;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "billing_key_deletion",
    uniqueConstraints = @UniqueConstraint(name = "uk_billing_key_deletion_key", columnNames = "billing_key_id"),
    indexes = @Index(name = "idx_billing_key_deletion_due", columnList = "status, next_check_at, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKeyDeletion extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_key_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_billing_key_deletion_key"))
    private BillingKey billingKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(24)")
    private BillingKeyDeletionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(24)")
    private BillingKeyDeletionReason reason;

    @Column(nullable = false, length = 100)
    private String storeId;
    @Column(nullable = false)
    private LocalDateTime requestedAt;
    @Column(nullable = false)
    private LocalDateTime nextCheckAt;
    @Column(nullable = false)
    private long claimToken;
    @Column(nullable = false)
    private int claimCount;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime firstDispatchedAt;
    private LocalDateTime completedAt;
    private LocalDateTime reviewRequiredAt;
    private LocalDateTime identityVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(40)")
    private BillingKeyDeletionCode lastCode;

    public static BillingKeyDeletion requested(BillingKey key, String storeId,
                                               BillingKeyDeletionReason reason, LocalDateTime now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(key.getId(), "key.id");
        Objects.requireNonNull(now, "now");
        if (storeId == null || storeId.isBlank() || storeId.length() > 100) {
            throw new IllegalArgumentException("삭제 대상 상점 설정이 올바르지 않습니다");
        }
        BillingKeyDeletion task = new BillingKeyDeletion();
        task.billingKey = key;
        task.storeId = storeId;
        task.reason = Objects.requireNonNull(reason, "reason");
        task.status = BillingKeyDeletionStatus.REQUESTED;
        task.requestedAt = now;
        task.nextCheckAt = now;
        return task;
    }

    public void recordVerifiedIdentity(LocalDateTime now) {
        if (identityVerifiedAt == null) {
            identityVerifiedAt = Objects.requireNonNull(now, "now");
        }
    }

    public boolean claim(LocalDateTime now) {
        if (status == BillingKeyDeletionStatus.COMPLETED
            || !BillingKeyDeletionPolicy.due(nextCheckAt, now)
            || (leaseExpiresAt != null && now.isBefore(leaseExpiresAt))) {
            return false;
        }
        claimToken = Math.incrementExact(claimToken);
        claimCount = Math.incrementExact(claimCount);
        leaseExpiresAt = now.plus(BillingKeyDeletionPolicy.LEASE);
        return true;
    }

    public boolean holdsClaim(long token, LocalDateTime now) {
        return status != BillingKeyDeletionStatus.COMPLETED
            && BillingKeyDeletionPolicy.liveLease(claimToken, token, leaseExpiresAt, now);
    }

    public boolean prepareDelete(long token, LocalDateTime now) {
        if (!holdsClaim(token, now) || status != BillingKeyDeletionStatus.REQUESTED) {
            return false;
        }
        if (!BillingKeyDeletionPolicy.deleteWindowOpen(requestedAt, now)) {
            review(BillingKeyDeletionCode.WINDOW_EXPIRED, now);
            return false;
        }
        if (firstDispatchedAt == null) {
            firstDispatchedAt = now;
        }
        return true;
    }

    public boolean defer(long token, LocalDateTime now, BillingKeyDeletionCode code) {
        if (!holdsClaim(token, now)) {
            return false;
        }
        if (status == BillingKeyDeletionStatus.REVIEW_REQUIRED
            || BillingKeyDeletionPolicy.requiresReview(code)
            || !BillingKeyDeletionPolicy.deleteWindowOpen(requestedAt, now)) {
            BillingKeyDeletionCode reasonCode = BillingKeyDeletionPolicy.requiresReview(code) ? code
                : (lastCode != null && BillingKeyDeletionPolicy.requiresReview(lastCode) ? lastCode
                   : BillingKeyDeletionCode.WINDOW_EXPIRED);
            review(reasonCode, now);
            return true;
        }
        lastCode = code;
        nextCheckAt = now.plus(BillingKeyDeletionPolicy.retryDelay(claimCount));
        leaseExpiresAt = null;
        return true;
    }

    // 운영 확인은 DELETE만 멈춘다. 동일 대상의 GET은 느린 주기로 계속해 콘솔 삭제도 정합화한다.
    public void review(BillingKeyDeletionCode code, LocalDateTime now) {
        if (status == BillingKeyDeletionStatus.COMPLETED) {
            return;
        }
        if (reviewRequiredAt == null) {
            reviewRequiredAt = now;
        }
        status = BillingKeyDeletionStatus.REVIEW_REQUIRED;
        lastCode = code;
        nextCheckAt = now.plus(BillingKeyDeletionPolicy.REVIEW_INTERVAL);
        leaseExpiresAt = null;
    }

    // 검증된 삭제 관측은 실행 토큰과 독립적이다. 호출자가 회원·키·요청 잠금과 대상 일치를 보장한다.
    public boolean complete(LocalDateTime now, BillingKeyDeletionCode evidence) {
        if (evidence != BillingKeyDeletionCode.DELETED && evidence != BillingKeyDeletionCode.BILLING_KEY_NOT_FOUND) {
            throw new IllegalArgumentException("삭제 확인 근거가 필요합니다");
        }
        if (status == BillingKeyDeletionStatus.COMPLETED) {
            return false;
        }
        status = BillingKeyDeletionStatus.COMPLETED;
        completedAt = now;
        lastCode = evidence;
        leaseExpiresAt = null;
        // NOT NULL 유지. COMPLETED는 복구 쿼리에서 제외된다.
        nextCheckAt = now;
        return true;
    }
}
