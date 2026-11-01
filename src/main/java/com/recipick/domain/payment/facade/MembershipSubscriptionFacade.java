package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.payment.facade.MembershipSubscriptionProcessor.SubscriptionReceipt;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipRequestHash;
import com.recipick.domain.payment.service.MembershipSubscribeResult;
import com.recipick.domain.payment.service.MembershipSubscribeStatus;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// C-11 바로 구독. 회원 도메인 Facade가 부른다 — 결제 쪽에 구독 HTTP Controller는 없다.
// 비트랜잭션 진입점이다(Propagation.NEVER) — 회원 구독 Facade는 바깥 트랜잭션 없이 C-11을 부른다. 바깥 TX가 있으면 TX1이 독립적으로
// 커밋되지 않은 채 PG까지 가므로 접수 행을 만들기 전에 거절한다. 필요한 쓰기 TX는 결제 Processor가 연다.
// 접수는 Processor(TX1), 청구는 Executor(선점 → PG → 재조회 → TX2).
// 같은 키 재요청은 플래그와 무관하게 기존 결과를 돌려주고, 신규 접수·새 PG POST만 플래그가 막는다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipSubscriptionFacade {

    private static final int MAX_KEY_LENGTH = 64;

    private final MembershipSubscriptionProcessor processor;
    private final MembershipChargeExecutor executor;
    private final Clock clock;

    @Transactional(propagation = Propagation.NEVER)
    public MembershipSubscribeResult subscribe(Long memberId, MembershipConsent consent, String idempotencyKey) {
        String storedKey = storedKey(memberId, idempotencyKey);
        if (consent == null) {
            throw new BusinessException(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
        }
        String requestHash = MembershipRequestHash.of(consent);
        LocalDateTime now = LocalDateTime.now(clock);
        SubscriptionReceipt receipt = processor.accept(memberId, consent, storedKey, requestHash, now);
        if (receipt.existingStatus() != null) {
            if (receipt.existingStatus() == MembershipSubscribeStatus.IN_PROGRESS && receipt.latestAttemptReady()) {
                // 같은 저장 시도의 실행권을 다시 잡아 본다. DISPATCHING이면 다른 Worker가 PG 호출 중이라 손대지 않는다
                return new MembershipSubscribeResult(receipt.membershipId(),
                    statusOf(executor.execute(memberId, receipt.attemptId())));
            }
            return new MembershipSubscribeResult(receipt.membershipId(), receipt.existingStatus());
        }
        MembershipChargeOutcome outcome = executor.execute(memberId, receipt.attemptId());
        return new MembershipSubscribeResult(receipt.membershipId(), statusOf(outcome));
    }

    // 키 필수(COMMON-007), 1~64자·공백 없음(COMMON-001), 소문자 정규화, 저장 키 "{memberId}:{key}"(다른 회원 회차는 조회 불가)
    private static String storedKey(Long memberId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_KEY_LENGTH || key.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return memberId + ":" + key.toLowerCase(Locale.ROOT);
    }

    private static MembershipSubscribeStatus statusOf(MembershipChargeOutcome outcome) {
        return switch (outcome) {
            case ACTIVATED -> MembershipSubscribeStatus.PAID;
            case CLOSED_UNPAID, UNAPPLIED_REFUND_REQUESTED -> MembershipSubscribeStatus.FAILED;
            case STILL_UNKNOWN, MANUAL_REVIEW, RECONCILE_HALTED -> MembershipSubscribeStatus.UNKNOWN;
            // 플래그 OFF·프로필 실패·다른 Worker가 처리 중·이미 확정(결과는 다음 조회로) — 모두 "처리 중"으로 안내
            case NOT_DISPATCHED, IN_PROGRESS, ALREADY_APPLIED, RETRY_READY ->
                MembershipSubscribeStatus.IN_PROGRESS;
        };
    }
}
