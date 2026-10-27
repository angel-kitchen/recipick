package com.recipick.domain.member.service;

import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberAddressRepository;
import com.recipick.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {
    private final MemberRepository members;
    private final MemberAddressRepository addresses;

    @Transactional(propagation = Propagation.MANDATORY)
    public void withdraw(Member member, LocalDateTime now) {
        member.withdraw(now);
        // 현재 배송지 FK를 먼저 해제한 다음 배송지를 삭제한다.
        members.flush();
        addresses.deleteAllByMemberId(member.getId());
    }
}
