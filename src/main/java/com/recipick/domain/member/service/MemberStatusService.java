package com.recipick.domain.member.service;

import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.MemberStatusChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberStatusService implements MemberStatusChecker {

    private final MemberRepository memberRepository;

    @Override
    public void requireActive(Long memberId) {
        if (!memberRepository.existsByIdAndWithdrawnAtIsNull(memberId)) {
            throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
        }
    }
}
