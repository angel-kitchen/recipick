package com.recipick.global.security;

public interface MemberStatusChecker {

    // 토큰 인증 시 현재 회원 상태를 확인하며, 이용할 수 없는 회원은 인증을 거절한다.
    void requireActive(Long memberId);
}
