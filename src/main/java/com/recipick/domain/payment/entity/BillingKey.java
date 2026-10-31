package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
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
import java.time.LocalDateTime;

import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "billing_key",
    // 이름을 명시해야 UNIQUE 충돌을 제약 이름으로 식별할 수 있다(BillingKeyService.saveNew).
    uniqueConstraints = @UniqueConstraint(
        name = "uk_billing_key_portone_billing_key",
        columnNames = "portone_billing_key"
    ),
    indexes = @Index(name = "idx_billing_key_member_id_status", columnList = "member_id, status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKey extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private String portoneBillingKey;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private BillingKeyStatus status;

    // PortOne이 발급을 확인한 시각(KST). 우리 저장 시각은 createdAt
    private LocalDateTime issuedAt;

    // PortOne 삭제 요청을 기록한 시각.
    private LocalDateTime deleteRequestedAt;

    // PortOne이 삭제를 확인한 시각.
    private LocalDateTime deletedAt;

    // 테스트 픽스처용. 운영 코드는 issued()로만 새 행을 만든다
    @Builder
    private BillingKey(Long memberId, String portoneBillingKey, BillingKeyStatus status, LocalDateTime issuedAt) {
        this.memberId = memberId;
        this.portoneBillingKey = portoneBillingKey;
        this.status = status;
        this.issuedAt = issuedAt;
    }

    // 등록의 새 행. 상태는 항상 ACTIVE로 시작한다
    public static BillingKey issued(Long memberId, String portoneBillingKey, LocalDateTime issuedAt) {
        return new BillingKey(memberId, portoneBillingKey, BillingKeyStatus.ACTIVE, issuedAt);
    }

    public boolean isActive() {
        return status == BillingKeyStatus.ACTIVE;
    }

    // 새 카드가 등록될 때의 이전 ACTIVE 키. 이미 REPLACED면 바꾸지 않고 false — 같은 처리가 두 번 지나가도 안전하다.
    // 그 밖의 상태는 잠금 밖에서 누가 바꿨다는 뜻이라 덮어쓰지 않고 예외로 드러낸다
    public boolean replace() {
        if (status == BillingKeyStatus.REPLACED) {
            return false;
        }
        if (status != BillingKeyStatus.ACTIVE) {
            throw new IllegalStateException("교체할 수 없는 빌링키 상태입니다: " + this);
        }
        status = BillingKeyStatus.REPLACED;
        return true;
    }

    // REPLACED 키를 다시 현재 수단으로. REVOKED·DELETE_REQUESTED·DELETED는 서비스가 먼저 BILLING-006으로 거르므로
    // 여기까지 오면 호출 순서 버그다
    public boolean reactivate() {
        if (status == BillingKeyStatus.ACTIVE) {
            return false;
        }
        if (status != BillingKeyStatus.REPLACED) {
            throw new IllegalStateException("되살릴 수 없는 빌링키 상태입니다: " + this);
        }
        status = BillingKeyStatus.ACTIVE;
        return true;
    }

    // 키 원문을 뺀다. 예외 메시지·로그에 이 문자열이 섞인다
    @Override
    public String toString() {
        return "BillingKey[id=" + id + ", memberId=" + memberId + ", status=" + status + "]";
    }
}
