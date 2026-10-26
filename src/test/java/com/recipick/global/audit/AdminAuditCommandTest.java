package com.recipick.global.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminAuditCommandTest {

    @Test
    @DisplayName("변경 정보는 복사되고 같은 수치의 소수 표현은 변경으로 기록하지 않는다")
    void copiesChangesAndNormalizesDecimalValues() {
        // given
        Map<AdminAuditField, AdminAuditChange> changes = new HashMap<>();
        changes.put(AdminAuditField.PRICE, new AdminAuditChange(1000L, 1200L));
        changes.put(AdminAuditField.SALE_UNIT_AMOUNT,
                new AdminAuditChange(new BigDecimal("1.00"), new BigDecimal("1.0")));
        AdminAuditCommand command = new AdminAuditCommand(AdminAuditAction.PRODUCT_UPDATE, 10L, 1L, changes);

        // when
        changes.clear();
        Map<String, Map<String, Object>> details = command.details();

        // then
        assertThat(details).containsOnlyKeys("price");
        assertThat(details.get("price")).containsEntry("before", 1000L).containsEntry("after", 1200L);
        assertThatThrownBy(() -> command.changes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> details.get("price").clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "user@example.com", "01012345678", "secret-token", "서울시 주소", "임의 사유"})
    @DisplayName("민감정보가 섞일 수 있는 임의 문자열은 변경값으로 받지 않는다")
    void rejectsFreeText(String value) {
        // given
        String input = value;

        // when
        var assertion = assertThatThrownBy(() -> new AdminAuditChange(input, 1L));

        // then
        assertion.isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("숫자 필드에 상태를 넣거나 다른 대상의 필드를 섞으면 거절한다")
    void rejectsWrongValueTypeAndTarget() {
        // given
        AdminAuditChange flag = new AdminAuditChange(false, true);
        AdminAuditChange number = new AdminAuditChange(1, 2);

        // when
        var wrongType = assertThatThrownBy(() -> new AdminAuditCommand(AdminAuditAction.PRODUCT_UPDATE,
                10L, 1L, Map.of(AdminAuditField.PRICE, flag)));
        var wrongTarget = assertThatThrownBy(() -> new AdminAuditCommand(AdminAuditAction.RECIPE_UPDATE,
                10L, null, Map.of(AdminAuditField.PRICE, number)));

        // then
        wrongType.isInstanceOf(BusinessException.class);
        wrongTarget.isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("목록 조회는 대상 ID와 변경값 없이 만들고 단건 작업은 양수 ID를 요구한다")
    void validatesTargetAndMartScope() {
        // given
        AdminAuditCommand list = new AdminAuditCommand(AdminAuditAction.MEMBER_LIST_VIEW, null, null, null);

        // when
        var missing = assertThatThrownBy(() -> new AdminAuditCommand(AdminAuditAction.RECIPE_UPDATE,
                null, null, null));
        var missingMart = assertThatThrownBy(() -> new AdminAuditCommand(AdminAuditAction.PRODUCT_UPDATE,
                10L, null, null));
        var wrongMart = assertThatThrownBy(() -> new AdminAuditCommand(AdminAuditAction.MART_STATUS_CHANGE,
                1L, 2L, null));

        // then
        assertThat(list.details()).isNull();
        assertThat(list.action().target()).isEqualTo(AdminAuditTarget.MEMBER);
        missing.isInstanceOf(BusinessException.class);
        missingMart.isInstanceOf(BusinessException.class);
        wrongMart.isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("시각 변경은 한국 시간 오프셋을 붙이며 원래 null 값도 보존한다")
    void convertsTimestampAndKeepsNullBefore() {
        // given
        LocalDateTime end = LocalDateTime.of(2026, 11, 25, 10, 0);
        AdminAuditCommand command = new AdminAuditCommand(AdminAuditAction.MEMBERSHIP_GRANT, 10L, null,
                Map.of(AdminAuditField.END_AT, new AdminAuditChange(null, end)));

        // when
        Map<String, Object> values = command.details().get("endAt");

        // then
        assertThat(values).containsEntry("before", null).containsEntry("after", "2026-11-25T10:00:00+09:00");
        assertThat(command.toString()).isEqualTo("AdminAuditCommand[REDACTED]");
        assertThat(command.changes().get(AdminAuditField.END_AT).toString()).isEqualTo("AdminAuditChange[REDACTED]");
    }

    @Test
    @DisplayName("변경 필드와 값이 모두 같으면 details를 비워두며 호출 여부는 업무 흐름이 결정한다")
    void omitsUnchangedValues() {
        // given
        AdminAuditCommand command = new AdminAuditCommand(AdminAuditAction.PRODUCT_PRICE_CHANGE, 10L, 1L,
                Map.of(AdminAuditField.PRICE, new AdminAuditChange(1000L, 1000L)));

        // when
        Map<String, Map<String, Object>> details = command.details();

        // then
        assertThat(details).isNull();
    }
}
