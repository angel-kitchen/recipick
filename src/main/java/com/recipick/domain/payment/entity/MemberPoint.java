package com.recipick.domain.payment.entity;

import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member_point")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberPoint extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long memberId;

    private Long balance;

    @Builder
    private MemberPoint(Long memberId, Long balance) {
        this.memberId = memberId;
        this.balance = balance;
    }
}
