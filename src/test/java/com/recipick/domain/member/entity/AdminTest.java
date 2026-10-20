package com.recipick.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminTest {

    @ParameterizedTest
    @CsvSource({"SUPER_ADMIN,", "MART_ADMIN,1"})
    @DisplayName("관리자 역할과 소속 마트가 일치하면 생성한다")
    void createsValidAdmin(AdminRole role, Long martId) {
        // given / when
        Admin admin = Admin.builder().role(role).martId(martId).active(true).build();
        // then
        assertThat(admin.getRole()).isEqualTo(role);
        assertThat(admin.getMartId()).isEqualTo(martId);
        assertThat(admin.isActive()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"SUPER_ADMIN,1", "MART_ADMIN,", "MART_ADMIN,0", "MART_ADMIN,-1", ",1"})
    @DisplayName("역할이 없거나 소속 마트 규칙이 맞지 않는 관리자 생성은 거부한다")
    void rejectsInvalidAdmin(AdminRole role, Long martId) {
        // given / when / then
        assertThatThrownBy(() -> Admin.builder().role(role).martId(martId).build())
                .isInstanceOf(BusinessException.class);
    }
}
