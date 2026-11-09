package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

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
    // UNIQUE는 암호문이 아니라 지문에 건다 — 암호문은 저장마다 달라진다
    uniqueConstraints = @UniqueConstraint(
        name = "uk_billing_key_portone_billing_key_hash",
        columnNames = "portone_billing_key_hash"
    ),
    indexes = @Index(name = "idx_billing_key_member_id_status", columnList = "member_id, status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKey extends BaseTimeEntity {

    // HMAC-SHA256 hex 길이. 컬럼 정의가 상수 식이어야 해서 BillingKeyFingerprint.LENGTH를 직접 참조하지 않는다
    static final int BILLING_KEY_HASH_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    // 원문은 메모리에만 있고 DB에는 암호문(v1:IV:암호문+태그)만 간다. 옛 평문 컬럼 portone_billing_key는 백필·Contract까지
    // 남지만 매핑하지 않는다. 운영 DDL은 Expand 단계라 NULL 허용이고, 기동 백필이 채운 뒤에만 요청을 받는다
    @Convert(converter = BillingKeyCiphertextConverter.class)
    @Column(name = "portone_billing_key_ciphertext", length = 512)
    private String portoneBillingKey;

    // 원문의 HMAC 지문. 같은 키 조회와 UNIQUE는 이 값으로 한다
    @Column(name = "portone_billing_key_hash", length = BILLING_KEY_HASH_LENGTH)
    private String portoneBillingKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private BillingKeyStatus status;

    // PortOne이 발급을 확인한 시각(KST). 우리 저장 시각은 createdAt
    private LocalDateTime issuedAt;

    // PortOne 삭제 요청을 기록한 시각.
    private LocalDateTime deleteRequestedAt;

    // PortOne이 삭제를 확인한 시각.
    private LocalDateTime deletedAt;

    // 테스트 픽스처용. 운영 코드는 issued()로만 새 행을 만든다. 지문 없이 만든 픽스처는 같은 키 조회에 걸리지 않는다
    @Builder
    private BillingKey(Long memberId, String portoneBillingKey, String portoneBillingKeyHash, BillingKeyStatus status,
                       LocalDateTime issuedAt) {
        this.memberId = memberId;
        this.portoneBillingKey = portoneBillingKey;
        this.portoneBillingKeyHash = portoneBillingKeyHash;
        this.status = status;
        this.issuedAt = issuedAt;
    }

    // 등록의 새 행. 상태는 항상 ACTIVE로 시작한다. 지문은 서비스가 원문으로 만들어 넘긴다 — 엔티티는 지문 키를 모른다
    public static BillingKey issued(Long memberId, String portoneBillingKey, String portoneBillingKeyHash,
                                    LocalDateTime issuedAt) {
        if (portoneBillingKeyHash == null || portoneBillingKeyHash.length() != BILLING_KEY_HASH_LENGTH) {
            throw new IllegalArgumentException("빌링키 지문이 올바르지 않습니다.");
        }
        return new BillingKey(memberId, portoneBillingKey, portoneBillingKeyHash, BillingKeyStatus.ACTIVE, issuedAt);
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

    // PortOne이 키 삭제·없음을 명확히 답했을 때. ACTIVE·REPLACED만 REVOKED로 바꾼다(true). 이미 REVOKED면 false(멱등).
    // 삭제 요청·삭제 완료는 더 나중 상태라 되돌리지 않고 false. 호출자가 회원·키를 잠그고 소유를 먼저 확인한다
    public boolean revoke() {
        if (status != BillingKeyStatus.ACTIVE && status != BillingKeyStatus.REPLACED) {
            return false;
        }
        status = BillingKeyStatus.REVOKED;
        return true;
    }

    // 원격 정리 접수. 호출자가 회원 → 키 순서로 잠그고 미해결 시도 검사를 끝낸 뒤 부른다. ACTIVE는 탈퇴 준비에서만 넘어온다.
    // 이미 삭제 요청·삭제 완료면 false(멱등) — 요청 시각은 최초 값만 남긴다
    public boolean requestDeletion(LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        if (status == BillingKeyStatus.DELETE_REQUESTED || status == BillingKeyStatus.DELETED) {
            return false;
        }
        if (status != BillingKeyStatus.ACTIVE && status != BillingKeyStatus.REPLACED
            && status != BillingKeyStatus.REVOKED) {
            throw new IllegalStateException("삭제 요청할 수 없는 빌링키 상태입니다: " + this);
        }
        status = BillingKeyStatus.DELETE_REQUESTED;
        deleteRequestedAt = now;
        return true;
    }

    // PortOne이 삭제를 확인한 증거가 있을 때만 삭제 Processor가 부른다. 행은 지우지 않는다 — 과거 결제 시도의 FK와 이력을 보존한다
    public boolean confirmRemoteDeletion(LocalDateTime confirmedAt) {
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        if (status == BillingKeyStatus.DELETED) {
            if (deletedAt == null) {
                throw new IllegalStateException("삭제 확인 시각이 없는 키입니다: " + this);
            }
            return false;
        }
        if (status != BillingKeyStatus.DELETE_REQUESTED) {
            throw new IllegalStateException("삭제 요청되지 않은 키입니다: " + this);
        }
        status = BillingKeyStatus.DELETED;
        deletedAt = confirmedAt;
        return true;
    }

    // 키 원문과 지문을 뺀다. 예외 메시지·로그에 이 문자열이 섞인다
    @Override
    public String toString() {
        return "BillingKey[id=" + id + ", memberId=" + memberId + ", status=" + status + "]";
    }
}
