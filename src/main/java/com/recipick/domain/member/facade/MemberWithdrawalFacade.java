package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.request.MemberWithdrawRequest;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberWithdrawalFacade {
    private final MemberWithdrawalProcessor processor;
    private final RedisRefreshTokenStore sessions;

    @Transactional(propagation = Propagation.NEVER)
    public void withdraw(Long memberId, MemberWithdrawRequest request) {
        List<String> previousSessions = sessions.findSessionKeys("MEMBER", memberId);
        processor.withdraw(memberId, request);
        try {
            sessions.deleteSessions(previousSessions);
        } catch (DataAccessException exception) {
            // 탈퇴 상태와 세션 버전으로 접근·재발급은 이미 거절된다. 남은 기록은 TTL로 정리된다.
            log.warn("회원 탈퇴 후 리프레시 세션 정리 실패: memberId={}", memberId, exception);
        }
    }
}
