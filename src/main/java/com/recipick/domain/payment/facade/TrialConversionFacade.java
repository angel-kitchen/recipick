package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.MembershipBillingTarget;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingGate;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.facade.TrialConversionProcessor.ConversionReceipt;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 체험 전환 스케줄러 본체. 비트랜잭션 진입점(Propagation.NEVER — 실행기와 같은 원칙). 플래그 OFF면 신규 청구를
// 접수하지 않는다(K-2).
// C-13 대상을 keyset으로 전부 읽고(카드 유무로 거르지 않는다 — 카드 없는 회원도 전환 대기를 끝내야 한다), 회원별 TX1 → 실행기.
// 건별 예외 격리: 한 회원의 실패가 다른 대상을 막지 않고, 그 회원은 다음 실행에서 다시 본다. 로그에는 ID·예외 클래스만
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialConversionFacade {

    private final MembershipBillingGate gate;
    private final MembershipBillingProperties properties;
    private final MembershipBillingService membershipBillingService;
    private final TrialConversionProcessor processor;
    private final MembershipChargeExecutor executor;
    private final Clock clock;

    // 처리한 대상 수(접수·종료·건너뜀 포함)
    @Transactional(propagation = Propagation.NEVER)
    public int runOnce() {
        if (!gate.newChargeEnabled()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int processed = 0;
        long afterId = 0;
        while (true) {
            List<MembershipBillingTarget> targets =
                membershipBillingService.findTrialConversionTargets(now, afterId, properties.batchSize());
            for (MembershipBillingTarget target : targets) {
                processed++;
                try {
                    ConversionReceipt receipt = processor.accept(target.memberId(), target.membershipId(), now);
                    if (receipt.kind() == ConversionReceipt.Kind.CHARGE_READY) {
                        executor.execute(target.memberId(), receipt.attemptId());
                    }
                } catch (RuntimeException e) {
                    log.warn("체험 전환 처리 실패(다음 실행에서 재확인): memberId={}, membershipId={}, cause={}",
                        target.memberId(), target.membershipId(), e.getClass().getSimpleName());
                }
            }
            if (targets.size() < properties.batchSize()) {
                return processed;
            }
            afterId = targets.get(targets.size() - 1).membershipId();
        }
    }
}
