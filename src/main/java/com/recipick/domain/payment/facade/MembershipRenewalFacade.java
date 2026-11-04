package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.MembershipBillingTarget;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingGate;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.facade.MembershipRenewalProcessor.CloseResult;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipPaymentRef;
import com.recipick.domain.payment.service.MembershipRenewalDecision;
import com.recipick.domain.payment.service.MembershipRenewalDecision.Kind;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 월 갱신 스케줄러 본체. 모든 진입점은 비트랜잭션(Propagation.NEVER) — 접수·판정은 Processor의 짧은 TX가 커밋한 뒤에만 공용
// 실행기가 PG를 부른다. 후보 조회 시각(scanNow)은 대상을 고르는 데만 쓰고 판정 시각은 Processor가 잠금 뒤 다시 읽는다.
// runOnce: 신규 청구(플래그 ON에서만) — C-2 신규 후보 접수 → 재개할 기존 회차(재시도 시각 도달·카드 대기·PENDING 잔존).
// settleOnce: 플래그 무관 — 마감 지난 회차와 해지·종료된 대기 회차의 안전 정리, 그리고 회차 없이 마감이 지난 이용권 정리.
//             PG 호출·새 시도 없음.
// resumeForMember: 카드 등록이 커밋된 뒤 그 회원의 카드 대기 회차만 재개. 어떤 실패도 밖으로 던지지 않는다(등록 성공은 그대로).
// 건별 예외 격리: 한 회원의 실패가 다른 대상을 막지 않고 다음 실행에서 다시 본다. 로그에는 ID·예외 클래스만 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRenewalFacade {

    private static final Set<MembershipPaymentStatus> UNRESOLVED = Set.of(MembershipPaymentStatus.PENDING,
        MembershipPaymentStatus.RETRY_WAITING, MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED,
        MembershipPaymentStatus.UNKNOWN, MembershipPaymentStatus.MANUAL_REVIEW);
    private static final Set<MembershipPaymentStatus> WAITING = Set.of(MembershipPaymentStatus.PENDING,
        MembershipPaymentStatus.RETRY_WAITING, MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED);

    private final MembershipBillingGate gate;
    private final MembershipBillingProperties properties;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentRepository payments;
    private final MembershipRenewalProcessor processor;
    private final MembershipChargeExecutor executor;
    private final Clock clock;

    // 처리한 대상 수(접수·재개·건너뜀 포함)
    @Transactional(propagation = Propagation.NEVER)
    public int runOnce() {
        if (!gate.newChargeEnabled()) {
            return 0;
        }
        LocalDateTime scanNow = LocalDateTime.now(clock);
        // 이번 실행에서 새로 만든 회차를 기존 회차 재개에서 다시 집지 않도록 상한을 먼저 고정한다
        long upperId = payments.findMaxId();
        int processed = 0;
        long afterId = 0;
        while (true) {
            List<MembershipBillingTarget> targets =
                membershipBillingService.findRenewalTargets(scanNow, afterId, properties.batchSize());
            for (MembershipBillingTarget target : targets) {
                processed++;
                try {
                    execute(target.memberId(), processor.accept(target.memberId(), target.membershipId()));
                } catch (RuntimeException e) {
                    logFailure("갱신 접수", target.membershipId(), null, e);
                }
            }
            if (targets.size() < properties.batchSize()) {
                break;
            }
            afterId = targets.get(targets.size() - 1).membershipId();
        }
        afterId = 0;
        while (true) {
            List<MembershipPaymentRef> refs = payments.findResumableRenewals(scanNow, afterId, upperId,
                PageRequest.of(0, properties.batchSize()));
            for (MembershipPaymentRef ref : refs) {
                processed++;
                try {
                    Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
                    execute(memberId, processor.resumeExisting(memberId, ref.membershipId(), ref.paymentId()));
                } catch (RuntimeException e) {
                    logFailure("갱신 재개", ref.membershipId(), ref.paymentId(), e);
                }
            }
            if (refs.size() < properties.batchSize()) {
                return processed;
            }
            afterId = refs.get(refs.size() - 1).paymentId();
        }
    }

    // 정리한 회차 수
    @Transactional(propagation = Propagation.NEVER)
    public int settleOnce() {
        LocalDateTime scanNow = LocalDateTime.now(clock);
        long upperId = payments.findMaxId();
        int processed = 0;
        long afterId = 0;
        while (true) {
            List<MembershipPaymentRef> refs = payments.findRenewalsNeedingMaintenance(scanNow, afterId, upperId,
                UNRESOLVED, WAITING, PageRequest.of(0, properties.batchSize()));
            for (MembershipPaymentRef ref : refs) {
                processed++;
                try {
                    Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
                    CloseResult result = processor.settleExisting(memberId, ref.membershipId(), ref.paymentId());
                    log.info("갱신 회차 정리: membershipPaymentId={}, outcome={}", ref.paymentId(), result.outcome());
                } catch (RuntimeException e) {
                    logFailure("갱신 회차 정리", ref.membershipId(), ref.paymentId(), e);
                }
            }
            if (refs.size() < properties.batchSize()) {
                break;
            }
            afterId = refs.get(refs.size() - 1).paymentId();
        }
        afterId = 0;
        while (true) {
            List<MembershipBillingTarget> targets =
                membershipBillingService.findOverdueRenewalTargets(scanNow, afterId, properties.batchSize());
            for (MembershipBillingTarget target : targets) {
                processed++;
                try {
                    CloseResult result = processor.settleOverdue(target.memberId(), target.membershipId());
                    log.info("마감 누락 갱신 정리: membershipId={}, outcome={}", target.membershipId(), result.outcome());
                } catch (RuntimeException e) {
                    logFailure("마감 누락 갱신 정리", target.membershipId(), null, e);
                }
            }
            if (targets.size() < properties.batchSize()) {
                return processed;
            }
            afterId = targets.get(targets.size() - 1).membershipId();
        }
    }

    // 카드 등록 Facade가 등록 TX 커밋 뒤에 부른다. 조회해 둔 회차 ID 그대로 재개하고, 그 사이 해결된 회차면 거기서 끝난다
    // (다음 기간 회차를 만들지 않는다). 목록 조회 실패까지 포함해 어떤 예외도 밖으로 내지 않는다
    @Transactional(propagation = Propagation.NEVER)
    public void resumeForMember(Long memberId) {
        try {
            if (!gate.newChargeEnabled()) {
                return;
            }
            List<Long> membershipIds = membershipBillingService.findMembershipIds(memberId);
            if (membershipIds.isEmpty()) {
                return;
            }
            List<MembershipPaymentRef> refs = payments.findRefsByMembershipIdInAndStatus(membershipIds,
                MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED);
            for (MembershipPaymentRef ref : refs) {
                try {
                    execute(memberId, processor.resumeExisting(memberId, ref.membershipId(), ref.paymentId()));
                } catch (RuntimeException e) {
                    logFailure("카드 등록 뒤 갱신 재개", ref.membershipId(), ref.paymentId(), e);
                }
            }
        } catch (RuntimeException e) {
            PaymentTaskFailureLogger.log(log, e, "카드 등록 뒤 갱신 재개 대상 조회 실패(다음 실행에서 재확인): memberId={}, cause={}", memberId,
                e.getClass().getSimpleName());
        }
    }

    private void execute(Long memberId, MembershipRenewalDecision decision) {
        if (decision.kind() == Kind.CHARGE_READY) {
            log.info("갱신 청구 실행: membershipPaymentId={}, attemptId={}, outcome={}", decision.membershipPaymentId(),
                decision.attemptId(), executor.execute(memberId, decision.attemptId()));
        }
    }

    // 이용권이 없다는 MEMBERSHIP-006은 정상 흐름에서 불가(회차는 이용권 ID로만 만든다) — 건너뛰고 ERROR(ID만)
    private static void logFailure(String step, Long membershipId, Long paymentId, RuntimeException e) {
        if (e instanceof BusinessException business
            && business.getErrorCode() == MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND) {
            log.error("{} 대상 이용권을 찾을 수 없어 건너뜀(운영 확인): membershipId={}, membershipPaymentId={}", step,
                membershipId, paymentId);
            return;
        }
        PaymentTaskFailureLogger.log(log, e, "{} 실패(다음 실행에서 재확인): membershipId={}, membershipPaymentId={}, cause={}", step, membershipId,
            paymentId, e.getClass().getSimpleName());
    }
}
