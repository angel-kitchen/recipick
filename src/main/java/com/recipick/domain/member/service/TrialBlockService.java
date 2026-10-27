package com.recipick.domain.member.service;

import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.repository.TrialBlockRepository;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrialBlockService {
    private final TrialBlockRepository blocks;
    private final MembershipRepository memberships;
    private final MemberRepository members;
    private final TrialPhoneHasher hasher;
    private final Clock clock;

    public boolean isBlocked(String phone) {
        return blocks.existsByPhoneHashAndExpiresAtAfter(hasher.hash(phone), LocalDateTime.now(clock));
    }

    // 호출자는 같은 트랜잭션에서 회원을 먼저 잠그고 개인정보 대체 전에 호출한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordWithdrawal(Long memberId, LocalDateTime withdrawnAt) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("체험 차단 기록에는 쓰기 트랜잭션이 필요합니다.");
        }
        if (withdrawnAt == null) {
            throw new IllegalArgumentException("탈퇴 시각이 필요합니다.");
        }
        Member member = members.findById(memberId).filter(value -> !value.isWithdrawn())
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (memberships.existsByMemberId(memberId)) {
            blocks.extendBlock(hasher.hash(member.getPhone()), withdrawnAt.plusYears(1), withdrawnAt);
        }
    }

    @Transactional
    public int deleteExpiredBatch(LocalDateTime now) {
        return blocks.deleteExpiredBatch(now);
    }
}
