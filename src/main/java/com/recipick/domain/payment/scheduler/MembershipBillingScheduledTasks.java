package com.recipick.domain.payment.scheduler;

import com.recipick.domain.payment.facade.MembershipChargeRecoveryFacade;
import com.recipick.domain.payment.facade.MembershipRefundRecoveryFacade;
import com.recipick.domain.payment.facade.MembershipRenewalFacade;
import com.recipick.domain.payment.facade.TrialConversionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 멤버십 자동결제의 주기 실행만 모아 둔다. 이 빈은 test 프로필에서 만들지 않는다 —
// 테스트는 runOnce()·recoverOnce()·settleOnce()를 직접 불러 실행 시점을 통제한다. 작업들은 전용 스케줄러(스레드 2개)에서 돈다.
// 신규 청구(체험 전환·갱신)는 플래그가 켜져 있을 때만, 정합화·정리(복구·갱신 회차 정리)는 플래그와 무관하게 돈다.
// 분산 락 없음 — 두 서버가 같은 대상을 집어도 MEMBER 잠금·재확인·회차 UNIQUE·선점 TX로 한 건만 처리된다.
// 주기는 SLA가 아니다 — 혜택 만료는 회원 도메인의 시각 판정으로 일어나고, 회차 정리가 정확히 마감 시각에 돈다고 가정하지 않는다
@Profile("!test")
@Component
@RequiredArgsConstructor
public class MembershipBillingScheduledTasks {

    private final TrialConversionFacade trialConversionFacade;
    private final MembershipChargeRecoveryFacade recoveryFacade;
    private final MembershipRenewalFacade renewalFacade;
    private final MembershipRefundRecoveryFacade refundRecoveryFacade;

    // 5분마다. 플래그는 Facade가 본다(OFF면 즉시 0)
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000,
        scheduler = MembershipBillingSchedulingConfig.MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public void convertTrials() {
        trialConversionFacade.runOnce();
    }

    // 5분마다. 플래그 무관 — 이미 생긴 결제의 정합화는 멈추지 않는다
    @Scheduled(fixedDelay = 300_000, initialDelay = 180_000,
        scheduler = MembershipBillingSchedulingConfig.MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public void recoverCharges() {
        recoveryFacade.recoverOnce();
    }

    // 5분마다. 월 갱신 신규 접수·재시도·카드 대기 재개. 플래그는 Facade가 본다(OFF면 즉시 0)
    @Scheduled(fixedDelay = 300_000, initialDelay = 150_000,
        scheduler = MembershipBillingSchedulingConfig.MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public void renewMemberships() {
        renewalFacade.runOnce();
    }

    // 5분마다. 플래그 무관 — 마감 지난 갱신 회차와 해지·종료된 대기 회차의 안전 정리(PG 호출·새 시도 없음)
    @Scheduled(fixedDelay = 300_000, initialDelay = 210_000,
        scheduler = MembershipBillingSchedulingConfig.MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public void settleRenewals() {
        renewalFacade.settleOnce();
    }

    // 1분마다. 플래그 무관 — 접수된 환불의 실행·재조회(큐 유실 복구 포함). 임대·다음 확인 시각·운영 표시로 중복 실행을 막는다
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000,
        scheduler = MembershipBillingSchedulingConfig.MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public void recoverRefunds() {
        refundRecoveryFacade.recoverOnce();
    }

    // 5분마다. 플래그 무관 — 환불 실행 기능이 없던 때 보존한 취소 웹훅을 재처리 대기로 되돌린다(되돌릴 것이 없으면 0)
    @Scheduled(fixedDelay = 300_000, initialDelay = 240_000,
        scheduler = MembershipBillingSchedulingConfig.MEMBERSHIP_BILLING_TASK_SCHEDULER)
    public void reopenPendingCancelWebhooks() {
        refundRecoveryFacade.reopenPendingCancelWebhooks();
    }
}
