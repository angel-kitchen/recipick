package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.BillingKeyIssueRequestStatus;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 서버가 로그인 회원에게 발급하는 일회성 카드 등록 요청. 프론트는 issueId를 SDK 발급에 그대로 넣고, 등록 때 PortOne이 돌려준
// issueId를 이 행과 대조해 "우리 서버가 이 회원을 위해 시작한 발급"임을 증명한다. 문자열 모양이 아니라 서버 기록이 증거다.
// USED 요청은 그 키에만 묶인다. 같은 키의 멱등 재요청(키 ACTIVE)은 유효기간과 무관하고, REPLACED 키를 되살리는 재활성화는
// 결제수단을 바꾸는 새 작업이라 발급 유효기간 안에서만 허용한다
@Getter
@Entity
@Table(
    name = "billing_key_issue_request",
    uniqueConstraints = @UniqueConstraint(name = "uk_billing_key_issue_request_issue_id", columnNames = "issue_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKeyIssueRequest extends BaseTimeEntity {

    // 발급 뒤 카드 입력에 쓸 수 있는 시간. 재활성화의 유효기간이기도 하다
    public static final Duration VALIDITY = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 64)
    private String issueId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private BillingKeyIssueRequestStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    // USED 때 묶인 키 행. 이 요청으로는 이 키만 다시 제출할 수 있다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_key_id", foreignKey = @ForeignKey(name = "fk_billing_key_issue_request_billing_key"))
    private BillingKey billingKey;

    private BillingKeyIssueRequest(Long memberId, String issueId, LocalDateTime now) {
        this.memberId = memberId;
        this.issueId = issueId;
        this.status = BillingKeyIssueRequestStatus.REQUESTED;
        this.expiresAt = now.plus(VALIDITY);
    }

    public static BillingKeyIssueRequest requested(Long memberId, String issueId, LocalDateTime now) {
        return new BillingKeyIssueRequest(memberId, issueId, now);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    // 발급 증빙의 유효기간. REQUESTED의 첫 사용과 USED의 재활성화가 같은 기준을 쓴다
    public boolean isWithinValidity(LocalDateTime now) {
        return now.isBefore(expiresAt);
    }

    public boolean isUsableAt(LocalDateTime now) {
        return status == BillingKeyIssueRequestStatus.REQUESTED && isWithinValidity(now);
    }

    public boolean isUsed() {
        return status == BillingKeyIssueRequestStatus.USED;
    }

    // USED 요청이 이 키에 묶여 있는가. 묶인 키는 JOIN FETCH 또는 트랜잭션 안에서만 읽는다(open-in-view: false)
    public boolean isBoundTo(String portoneBillingKey) {
        return status == BillingKeyIssueRequestStatus.USED
            && billingKey != null
            && billingKey.getPortoneBillingKey().equals(portoneBillingKey);
    }

    // 이 요청으로 지금 이 키를 제출할 수 있는가
    // REQUESTED: 유효기간 안의 첫 사용만. USED: 묶인 키의 재제출만 — 묶인 키가 REPLACED면 되살리기(결제수단 변경)라
    // 유효기간 안에서만, 그 밖의 상태면 상태를 바꾸지 않는 멱등 재요청(ACTIVE)이거나 서비스가 BILLING-006으로 거를 상태라 유효기간과 무관하다
    public boolean allowsSubmissionOf(String portoneBillingKey, LocalDateTime now) {
        if (status == BillingKeyIssueRequestStatus.REQUESTED) {
            return isWithinValidity(now);
        }
        if (!isBoundTo(portoneBillingKey)) {
            return false;
        }
        return billingKey.getStatus() != BillingKeyStatus.REPLACED || isWithinValidity(now);
    }

    // 첫 사용. 두 번 부르면 호출 순서 버그다 — USED·같은 키 재제출은 이 메서드를 부르지 않는다
    public void markUsed(BillingKey key, LocalDateTime now) {
        if (status != BillingKeyIssueRequestStatus.REQUESTED) {
            throw new IllegalStateException("이미 사용된 카드 등록 요청입니다: " + this);
        }
        this.status = BillingKeyIssueRequestStatus.USED;
        this.usedAt = now;
        this.billingKey = key;
    }

    // 토큰(issueId)을 뺀다
    @Override
    public String toString() {
        return "BillingKeyIssueRequest[id=" + id + ", memberId=" + memberId + ", status=" + status + "]";
    }
}
