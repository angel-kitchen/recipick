package com.recipick.domain.member.service;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.SecurityEvents;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.redis.RedisLoginAttemptStore;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SensitivePasswordGuard {
    // 로그인 계정 잠금과 분리하며 비밀번호 변경·탈퇴가 같은 제한을 공유한다.
    private static final String NAMESPACE = "MEMBER_REAUTH";
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final RedisLoginAttemptStore attempts;
    private final PasswordEncoder encoder;

    public void requireAvailable(Long memberId) {
        if (attempts.isLocked(NAMESPACE, memberId.toString())) {
            SecurityEvents.rejected(SecurityEvents.Event.PASSWORD_CHECK_LOCKED, SubjectType.MEMBER, memberId);
            throw new BusinessException(MemberErrorCode.REAUTHENTICATION_LOCKED);
        }
    }

    public void verify(Long memberId, String password, String hash) {
        requireAvailable(memberId);
        if (!encoder.matches(password, hash)) {
            boolean locked = attempts.recordFailure(NAMESPACE, memberId.toString(), MAX_FAILURES, WINDOW, WINDOW);
            SecurityEvents.rejected(SecurityEvents.Event.PASSWORD_CHECK_FAILED, SubjectType.MEMBER, memberId);
            throw new BusinessException(locked ? MemberErrorCode.REAUTHENTICATION_LOCKED
                    : MemberErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (!attempts.clearIfUnlocked(NAMESPACE, memberId.toString())) {
            throw new BusinessException(MemberErrorCode.REAUTHENTICATION_LOCKED);
        }
    }
}
