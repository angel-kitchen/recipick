package com.recipick.domain.member.entity;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "admin")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, unique = true)
    private String loginId;

    private String passwordHash;

    @Column(length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private AdminRole role;

    private Long martId;

    private boolean active;

    @Builder
    private Admin(String loginId, String passwordHash, String name, AdminRole role, Long martId, boolean active) {
        if (role == null
                || (role == AdminRole.SUPER_ADMIN && martId != null)
                || (role == AdminRole.MART_ADMIN && (martId == null || martId <= 0))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.martId = martId;
        this.active = active;
    }
}
