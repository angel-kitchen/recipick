package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.request.MemberPasswordChangeRequest;
import com.recipick.domain.member.service.MemberPasswordService;
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
public class MemberPasswordFacade {

    private final MemberPasswordService passwordService;
    private final RedisRefreshTokenStore refreshTokenStore;

    @Transactional(propagation = Propagation.NEVER)
    public void changePassword(Long memberId, MemberPasswordChangeRequest request) {
        // 커밋 후 새 비밀번호로 시작한 세션을 삭제하지 않도록 정리할 키를 미리 고정한다.
        List<String> previousSessions = refreshTokenStore.findSessionKeys("MEMBER", memberId);
        passwordService.changePassword(memberId, request);
        try {
            refreshTokenStore.deleteSessions(previousSessions);
        } catch (DataAccessException exception) {
            // DB 버전으로 이전 토큰은 이미 무효화됐다. 남은 Redis 기록은 TTL로 만료된다.
            log.warn("비밀번호 변경 후 리프레시 세션 기록 정리 실패: memberId={}", memberId, exception);
        }
    }
}
