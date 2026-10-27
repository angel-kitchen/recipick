package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.MemberLoginResult;
import com.recipick.domain.member.dto.request.MemberLoginRequest;
import com.recipick.domain.member.dto.response.MemberLoginResponse;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.config.LoginProperties;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.JwtTokenPair;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.infra.redis.RedisLoginAttemptStore;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MemberLoginService {

    private static final String SUBJECT_TYPE = "MEMBER";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisLoginAttemptStore attemptStore;
    private final RedisRefreshTokenStore refreshTokenStore;
    private final JwtTokenProvider tokenProvider;
    private final LoginProperties properties;
    private final String dummyPasswordHash;

    public MemberLoginService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            RedisLoginAttemptStore attemptStore, RedisRefreshTokenStore refreshTokenStore,
            JwtTokenProvider tokenProvider, LoginProperties properties) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.attemptStore = attemptStore;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        // 없는 이메일도 해시 비교를 수행해 조회 결과에 따른 응답 시간 차이를 줄인다.
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public MemberLoginResult login(MemberLoginRequest request) {
        if (attemptStore.isLocked(SUBJECT_TYPE, request.email())) {
            throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
        }
        Member member = memberRepository.findByEmailIgnoreCase(request.email())
                .filter(value -> !value.isWithdrawn()).orElse(null);
        // DB가 같은 계정으로 비교하는 표기가 달라도 잠금은 저장된 이메일로 모은다.
        String attemptId = member == null ? request.email()
                : member.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!attemptId.equals(request.email()) && attemptStore.isLocked(SUBJECT_TYPE, attemptId)) {
            throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
        }
        String hash = member == null ? dummyPasswordHash : member.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (member == null || !matches) {
            boolean locked = attemptStore.recordFailure(SUBJECT_TYPE, attemptId, properties.maxFailures(),
                    properties.failureExpiration(), properties.lockDuration());
            throw new BusinessException(locked ? AuthErrorCode.LOGIN_LOCKED : AuthErrorCode.INVALID_MEMBER_CREDENTIALS);
        }
        // 비밀번호 검사 중 다른 요청이 잠금을 만들었으면 토큰을 발급하지 않는다.
        if (!attemptStore.clearIfUnlocked(SUBJECT_TYPE, attemptId)) {
            throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
        }
        JwtTokenPair tokens = tokenProvider.createMemberTokens(member.getId(), member.getRefreshTokenVersion());
        refreshTokenStore.save(SUBJECT_TYPE, member.getId(), tokens.refreshTokenId(), tokens.refreshToken(),
                tokens.refreshExpiration());
        MemberLoginResponse response = new MemberLoginResponse(tokens.accessToken(),
                tokens.accessExpiration().toSeconds(), member.getId());
        return new MemberLoginResult(response, tokens.refreshToken(), tokens.refreshExpiration());
    }
}
