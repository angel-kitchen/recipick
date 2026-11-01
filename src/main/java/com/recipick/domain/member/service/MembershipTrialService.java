package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.dto.response.MembershipTrialStartResponse;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.repository.TrialBlockRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipTrialService {
    private static final int TRIAL_DAYS = 7;
    private static final long MONTHLY_AMOUNT = 3900L;

    private final MembershipRepository memberships;
    private final TrialBlockRepository blocks;
    private final TrialPhoneHasher hasher;

    // 최종 Facade가 첫 DB 작업으로 회원을 잠그고 활성 카드·미해결 결제를 확인한 뒤 호출한다.
    // 해당 결제 조회 계약 연결 전에는 Controller에서 이 서비스를 호출하지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipTrialStartResponse start(Member member, MembershipConsent consent, LocalDateTime now) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("체험 시작은 쓰기 가능한 바깥 트랜잭션이 필요합니다.");
        }
        if (member == null || member.getId() == null || now == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (member.isWithdrawn()) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        if (consent == null || !Long.valueOf(MONTHLY_AMOUNT).equals(consent.consentedAmount())
                || consent.consentTextVersion() == null || consent.consentTextVersion().isBlank()
                || consent.consentTextVersion().length() > 20 || consent.autopayConsentedAt() == null
                || !now.equals(consent.autopayConsentedAt())) {
            throw new BusinessException(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
        }
        if (memberships.existsBlocking(member.getId(), now)) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
        }
        if (memberships.existsByMemberIdAndActivatedAtIsNotNull(member.getId())
                || blocks.existsByPhoneHashAndExpiresAtAfter(hasher.hash(member.getPhone()), now)) {
            throw new BusinessException(MemberErrorCode.TRIAL_NOT_ELIGIBLE);
        }
        Membership trial = memberships.save(Membership.builder().member(member).type(MembershipType.TRIAL)
                .source(MembershipSource.TRIAL).startAt(now).activatedAt(now).endAt(now.plusDays(TRIAL_DAYS))
                .autoRenew(true).consentedAmount(consent.consentedAmount())
                .consentTextVersion(consent.consentTextVersion()).autopayConsentedAt(consent.autopayConsentedAt()).build());
        return new MembershipTrialStartResponse(trial.getId(), trial.getType().name(), trial.getSource().name(),
                trial.getStartAt(), trial.getEndAt(), trial.isAutoRenew());
    }
}
