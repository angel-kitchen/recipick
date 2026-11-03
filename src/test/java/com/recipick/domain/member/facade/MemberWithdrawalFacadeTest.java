package com.recipick.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.dto.request.MemberWithdrawRequest;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class MemberWithdrawalFacadeTest {

    private final MemberWithdrawalProcessor service = mock(MemberWithdrawalProcessor.class);
    private final MemberWithdrawalPreparationProcessor preparation = mock(MemberWithdrawalPreparationProcessor.class);
    private final RedisRefreshTokenStore sessions = mock(RedisRefreshTokenStore.class);
    private final MemberWithdrawalFacade facade = new MemberWithdrawalFacade(service, preparation, sessions);
    private final MemberWithdrawRequest request = new MemberWithdrawRequest("Original1!", 0L);

    @Test
    @DisplayName("DB 커밋 후 Redis 정리가 실패해도 탈퇴가 완료된 상태을 실패로 되돌리지 않는다")
    void keepsCommittedChangeWhenCleanupFails() {
        // given
        List<String> keys = List.of("old-session");
        given(sessions.findSessionKeys("MEMBER", 1L)).willReturn(keys);
        doThrow(new DataAccessResourceFailureException("검증용 Redis 오류")).when(sessions).deleteSessions(keys);

        // when
        facade.withdraw(1L, request);

        // then
        var order = inOrder(sessions, service);
        order.verify(sessions).findSessionKeys("MEMBER", 1L);
        order.verify(service).withdraw(1L, request);
        order.verify(sessions).deleteSessions(keys);
    }

    @Test
    @DisplayName("Redis 세션 목록을 확보하지 못하면 DB 회원 탈퇴을 시작하지 않는다")
    void doesNotWithdrawWhenSnapshotFails() {
        // given
        given(sessions.findSessionKeys("MEMBER", 1L))
                .willThrow(new DataAccessResourceFailureException("검증용 Redis 오류"));

        // when, then
        assertThatThrownBy(() -> facade.withdraw(1L, request))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(service);
    }
    @Test
    @DisplayName("카드 정리 준비가 완료돼야 최종 탈퇴와 세션 삭제를 순서대로 실행한다")
    void preparesBeforeFinalWithdrawal() {
        // given
        given(preparation.isEnabled()).willReturn(true);
        given(preparation.prepare(1L, request)).willReturn(
                com.recipick.domain.member.service.WithdrawalPaymentMethodPreparation.ready());
        given(sessions.findSessionKeys("MEMBER", 1L)).willReturn(List.of("old"));
        // when
        facade.withdraw(1L, request);
        // then
        var order = inOrder(preparation, service, sessions);
        order.verify(sessions).findSessionKeys("MEMBER", 1L);
        order.verify(preparation).isEnabled();
        order.verify(preparation).prepare(1L, request);
        order.verify(service).withdraw(1L, request);
        order.verify(sessions).deleteSessions(List.of("old"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value =
            com.recipick.domain.member.service.WithdrawalPaymentMethodPreparation.Status.class,
            names = {"PENDING", "REVIEW_REQUIRED"})
    @DisplayName("카드 정리 대기·운영 확인은 최종 탈퇴와 세션 삭제 없이 준비 결과 오류를 반환한다")
    void refusesPendingPreparation(com.recipick.domain.member.service.WithdrawalPaymentMethodPreparation.Status status) {
        // given
        var error = com.recipick.domain.payment.exception.BillingKeyDeletionErrorCode.CLEANUP_PENDING;
        given(preparation.isEnabled()).willReturn(true);
        given(preparation.prepare(1L, request)).willReturn(
                new com.recipick.domain.member.service.WithdrawalPaymentMethodPreparation(status, error));
        // when, then
        assertThatThrownBy(() -> facade.withdraw(1L, request))
                .isInstanceOfSatisfying(com.recipick.global.exception.BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(error));
        verifyNoInteractions(service);
        org.mockito.Mockito.verify(sessions, org.mockito.Mockito.never()).deleteSessions(org.mockito.ArgumentMatchers.any());
    }

}
