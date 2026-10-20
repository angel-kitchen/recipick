package com.recipick.domain.member.entity;

import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member_address")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAddress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 30)
    private String label;

    @Column(length = 255, nullable = false)
    private String address;

    @Column(length = 255, nullable = false)
    private String detailAddress;

    @Column(length = 10, nullable = false)
    private String legalDongCode;

    @Builder
    private MemberAddress(Member member, String label, String address, String detailAddress, String legalDongCode) {
        if (member == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.member = member;
        rename(label);
        changeAddress(address, detailAddress, legalDongCode);
    }

    public boolean isOwnedBy(Member owner) {
        return owner != null && (member == owner
                || owner.getId() != null && owner.getId().equals(member.getId()));
    }

    public void rename(String label) {
        if (label != null && label.length() > 30) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.label = label;
    }

    public void changeAddress(String address, String detailAddress, String legalDongCode) {
        if (address == null || address.isBlank() || address.length() > 255
                || detailAddress == null || detailAddress.isBlank() || detailAddress.length() > 255
                || legalDongCode == null || !legalDongCode.matches("[0-9]{10}")) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.address = address;
        this.detailAddress = detailAddress;
        this.legalDongCode = legalDongCode;
    }
}
