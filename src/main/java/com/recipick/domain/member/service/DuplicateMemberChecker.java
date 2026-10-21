package com.recipick.domain.member.service;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DuplicateMemberChecker {

    private final MemberRepository memberRepository;

    // 실패한 INSERT 트랜잭션과 분리해, 기존 제약 이름으로 발생한 중복도 확인한다.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public MemberErrorCode findConflict(String email, String phone) {
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            return MemberErrorCode.DUPLICATE_EMAIL;
        }
        if (memberRepository.existsByPhone(phone)) {
            return MemberErrorCode.DUPLICATE_PHONE;
        }
        return null;
    }
}
