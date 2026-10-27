package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.request.MemberPasswordChangeRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberPasswordService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(Long memberId, MemberPasswordChangeRequest request) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .filter(value -> !value.isWithdrawn())
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.INVALID_CURRENT_PASSWORD);
        }
        member.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }
}
