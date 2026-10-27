package com.recipick.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.dto.request.MemberPasswordChangeRequest;
import com.recipick.domain.member.service.MemberPasswordService;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class MemberPasswordFacadeTest {

    private final MemberPasswordService service = mock(MemberPasswordService.class);
    private final RedisRefreshTokenStore sessions = mock(RedisRefreshTokenStore.class);
    private final MemberPasswordFacade facade = new MemberPasswordFacade(service, sessions);
    private final MemberPasswordChangeRequest request = new MemberPasswordChangeRequest("Original1!", "Changed22!");

    @Test
    @DisplayName("DB 커밋 후 Redis 정리가 실패해도 버전 무효화가 완료된 변경을 실패로 되돌리지 않는다")
    void keepsCommittedChangeWhenCleanupFails() {
        // given
        List<String> keys = List.of("old-session");
        given(sessions.findSessionKeys("MEMBER", 1L)).willReturn(keys);
        doThrow(new DataAccessResourceFailureException("검증용 Redis 오류")).when(sessions).deleteSessions(keys);

        // when
        facade.changePassword(1L, request);

        // then
        var order = inOrder(sessions, service);
        order.verify(sessions).findSessionKeys("MEMBER", 1L);
        order.verify(service).changePassword(1L, request);
        order.verify(sessions).deleteSessions(keys);
    }

    @Test
    @DisplayName("Redis 세션 목록을 확보하지 못하면 DB 비밀번호 변경을 시작하지 않는다")
    void doesNotChangePasswordWhenSnapshotFails() {
        // given
        given(sessions.findSessionKeys("MEMBER", 1L))
                .willThrow(new DataAccessResourceFailureException("검증용 Redis 오류"));

        // when, then
        assertThatThrownBy(() -> facade.changePassword(1L, request))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(service);
    }
}
