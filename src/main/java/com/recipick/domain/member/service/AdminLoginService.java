package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.AdminLoginResult;
import com.recipick.domain.member.dto.request.AdminLoginRequest;
import com.recipick.domain.member.dto.response.AdminLoginResponse;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.global.config.LoginProperties;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenPair;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.redis.RedisLoginAttemptStore;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminLoginService {

    private static final String SUBJECT_TYPE = "ADMIN";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisLoginAttemptStore attemptStore;
    private final RedisRefreshTokenStore refreshTokenStore;
    private final JwtTokenProvider tokenProvider;
    private final LoginProperties properties;
    private final String dummyPasswordHash;

    public AdminLoginService(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
            RedisLoginAttemptStore attemptStore, RedisRefreshTokenStore refreshTokenStore,
            JwtTokenProvider tokenProvider, LoginProperties properties) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.attemptStore = attemptStore;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        // 없는 아이디도 해시 비교를 수행해 계정 존재 여부에 따른 시간 차이를 줄인다.
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AdminLoginResult login(AdminLoginRequest request) {
        if (attemptStore.isLocked(SUBJECT_TYPE, request.loginId())) {
            throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
        }
        Admin admin = adminRepository.findByLoginIdIgnoreCase(request.loginId()).orElse(null);
        // DB가 같은 계정으로 비교하는 표기가 달라도 실패 횟수는 저장된 아이디로 모은다.
        String attemptId = admin == null ? request.loginId()
                : admin.getLoginId().trim().toLowerCase(Locale.ROOT);
        if (!attemptId.equals(request.loginId()) && attemptStore.isLocked(SUBJECT_TYPE, attemptId)) {
            throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
        }
        String hash = admin == null ? dummyPasswordHash : admin.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (admin == null || !matches) {
            boolean locked = attemptStore.recordFailure(SUBJECT_TYPE, attemptId, properties.maxFailures(),
                    properties.failureExpiration(), properties.lockDuration());
            throw new BusinessException(locked ? AuthErrorCode.LOGIN_LOCKED : AuthErrorCode.INVALID_ADMIN_CREDENTIALS);
        }
        if (!admin.isActive()) {
            throw new BusinessException(AuthErrorCode.INACTIVE_ADMIN);
        }
        if (!attemptStore.clearIfUnlocked(SUBJECT_TYPE, attemptId)) {
            throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
        }
        AuthPrincipal principal = new AuthPrincipal(SubjectType.ADMIN, admin.getId(),
                admin.getRole().name(), admin.getMartId());
        JwtTokenPair pair = tokenProvider.createTokens(principal);
        refreshTokenStore.save(SUBJECT_TYPE, admin.getId(), pair.refreshTokenId(), pair.refreshToken(),
                pair.refreshExpiration());
        AdminLoginResponse response = new AdminLoginResponse(pair.accessToken(), pair.accessExpiration().toSeconds(),
                admin.getId(), admin.getRole().name(), admin.getMartId());
        return new AdminLoginResult(response, pair.refreshToken(), pair.refreshExpiration());
    }
}
