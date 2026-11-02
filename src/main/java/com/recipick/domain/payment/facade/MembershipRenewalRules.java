package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.BillingMembershipData;
import com.recipick.domain.member.dto.MembershipBillingResult;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingGate;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipBillingPeriods.RenewalPeriod;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.domain.payment.service.MembershipRenewalDecision;
import com.recipick.domain.payment.service.MembershipRenewalDecision.Kind;
import com.recipick.global.common.PolicyConstants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// 이미 있는 갱신 회차를 잠금 아래에서 어떻게 이어 갈지 정하는 공통 규칙. 접수(TX1)·결과 반영(TX2)·READY 만료·정리 작업이 모두
// 같은 순서를 쓴다: 시도 전부 PK 오름차순 잠금 → 만료 READY 취소 → 살아 있는 시도 확인 → 종료 조건(해지·종료 → 마감 → 발송 한도)
// → 그다음에만 재시도 시각·동의·플래그·재시도 가드·카드. 종료 조건을 대기보다 먼저 보는 이유는 해지된 대기 회차가 예약 시각만
// 기다리며 남지 않게 하려는 것이다. 이 클래스의 메서드는 모두 호출자의 쓰기 트랜잭션 안에서만 돈다(MANDATORY) — 잠금 순서
// MEMBER → MEMBERSHIP → MEMBERSHIP_PAYMENT는 호출자가 이미 잡았다. PG는 부르지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRenewalRules {

    public static final String REASON_ATTEMPTS_EXHAUSTED = "RENEWAL_ATTEMPTS_EXHAUSTED";
    public static final String REASON_DEADLINE_PASSED = "RENEWAL_DEADLINE_PASSED";
    public static final String REASON_RENEWAL_CANCELED = "RENEWAL_CANCELED";
    public static final String REASON_MEMBERSHIP_TERMINATED = "MEMBERSHIP_TERMINATED";
    public static final String REASON_NOT_DISPATCHED = "NOT_DISPATCHED";

    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentQueryService membershipPaymentQueryService;
    private final MembershipPaymentRepository payments;
    private final MembershipPaymentAttemptRepository attempts;
    private final BillingKeyRepository billingKeys;
    private final MembershipBillingGate gate;
    private final MembershipBillingProperties properties;

    // CHARGE: 새 시도까지 만들 수 있다(신규 청구 경로). SETTLE: 취소·종료·대기 정규화만(정리 작업, 플래그 무관)
    public enum Mode { CHARGE, SETTLE }

    // 회차 PK를 잠근 뒤 부른다. 시도 ID만 일반 조회하고 PK 오름차순으로 한 행씩 잠근다 — 판정은 이 목록으로만 한다
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MembershipPaymentAttempt> lockAttempts(MembershipPayment payment) {
        List<MembershipPaymentAttempt> locked = new ArrayList<>();
        for (Long attemptId : attempts.findIdsByMembershipPaymentIdOrderByIdAsc(payment.getId())) {
            MembershipPaymentAttempt attempt = attempts.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new IllegalStateException("잠그려던 시도가 사라짐: attemptId=" + attemptId));
            if (!attempt.getMembershipPayment().getId().equals(payment.getId())) {
                throw new IllegalStateException("회차와 시도 연결이 맞지 않음: attemptId=" + attemptId);
            }
            locked.add(attempt);
        }
        return locked;
    }

    // 기존 갱신 회차 이어 가기. now는 호출자가 모든 잠금을 잡은 뒤 읽은 시각이다
    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipRenewalDecision continueExisting(Long memberId, BillingMembershipData membership,
                                                      MembershipPayment payment, List<MembershipPaymentAttempt> locked,
                                                      LocalDateTime now, Mode mode) {
        Long paymentId = payment.getId();
        if (!payment.isUnresolved()) {
            return MembershipRenewalDecision.of(Kind.ALREADY_RESOLVED, paymentId);
        }
        RenewalPeriod period = storedPeriod(payment);
        if (locked.stream().anyMatch(MembershipPaymentAttempt::isAutoReconcileHalted)) {
            return halted(paymentId, "AUTO_RECONCILE_HALTED");
        }
        cancelExpiredReady(locked, now);
        if (hasPgPending(locked)) {
            return MembershipRenewalDecision.of(Kind.PG_PENDING, paymentId);
        }
        List<MembershipPaymentAttempt> validReady = validReady(locked, now);
        if (validReady.size() > 1) {
            // 모순 데이터. 아무거나 골라 한 번 더 청구하지 않는다
            return halted(paymentId, "MULTIPLE_VALID_READY");
        }
        if (validReady.size() == 1) {
            return keepValidReady(membership, payment, period, validReady.get(0), mode);
        }
        if (locked.stream().anyMatch(a -> a.getStatus() == MembershipPaymentAttemptStatus.PAID)) {
            // 미해결 회차에 수납된 시도가 있는데 반영·환불 관계가 설명되지 않는다. 재접수·FAILED 종료로 정산을 풀지 않는다
            return halted(paymentId, "UNEXPLAINED_PAID_ATTEMPT");
        }
        if (!stoppable(membership) || !matches(period, membership)) {
            return halted(paymentId, "MEMBERSHIP_PERIOD_MISMATCH");
        }
        if (!isWaitingStatus(payment.getStatus())) {
            return halted(paymentId, "NO_LIVE_ATTEMPT_ON_" + payment.getStatus().name());
        }
        String terminal = terminalReason(membership, period, locked, now);
        if (terminal != null) {
            closeRenewal(payment, membership, terminal);
            return MembershipRenewalDecision.of(Kind.CLOSED, paymentId);
        }
        if (payment.getStatus() == MembershipPaymentStatus.PENDING && !normalizeResidue(payment, locked, period, now)) {
            return halted(paymentId, "UNEXPLAINED_PENDING");
        }
        if (mode == Mode.SETTLE) {
            return MembershipRenewalDecision.of(Kind.IN_PROGRESS, paymentId);
        }
        return reissue(memberId, membership, payment, locked, period, now);
    }

    // 결과 반영(TX2)이 이 회차의 한 시도를 FAILED로 막 확정한 뒤. 다른 시도가 살아 있으면 회차를 그대로 두고, 아니면 종료 조건을
    // 먼저 본 뒤 다음 청구 시각 = 이 실패의 확정 시각 + R(마감 이상이면 예약 없음)으로 재시도 대기에 둔다. PG는 부르지 않는다
    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipRenewalDecision afterFailure(BillingMembershipData membership, MembershipPayment payment,
                                                  List<MembershipPaymentAttempt> locked,
                                                  MembershipPaymentAttempt failed, LocalDateTime now) {
        Long paymentId = payment.getId();
        cancelExpiredReady(locked, now);
        if (hasPgPending(locked)) {
            return MembershipRenewalDecision.of(Kind.PG_PENDING, paymentId);
        }
        if (!validReady(locked, now).isEmpty()) {
            return MembershipRenewalDecision.of(Kind.IN_PROGRESS, paymentId);
        }
        RenewalPeriod period = storedPeriod(payment);
        if (locked.stream().anyMatch(a -> a.getStatus() == MembershipPaymentAttemptStatus.PAID)) {
            return halted(paymentId, "UNEXPLAINED_PAID_ATTEMPT");
        }
        if (!stoppable(membership) || !matches(period, membership)) {
            return halted(paymentId, "MEMBERSHIP_PERIOD_MISMATCH");
        }
        String terminal = terminalReason(membership, period, locked, now);
        if (terminal != null) {
            closeRenewal(payment, membership, terminal);
            return MembershipRenewalDecision.of(Kind.CLOSED, paymentId);
        }
        LocalDateTime next = failed.getCompletedAt().plus(properties.retryInterval());
        payment.waitForRetry(next.isBefore(period.graceDeadlineAt()) ? next : null, failed.getFailureReason());
        return MembershipRenewalDecision.of(Kind.WAITING_RETRY, paymentId);
    }

    // C-4b. 이 회차가 정상 유료·활성 이용권의 현재 갱신일 때만 부른다. RENEWAL_STOPPED·ALREADY_APPLIED만 정상이고
    // 그 전제를 확인했는데 CONFLICT면 숨기지 않고 예외(트랜잭션 전체 롤백)
    @Transactional(propagation = Propagation.MANDATORY)
    public void stopRenewal(Long membershipId) {
        MembershipBillingResult result = membershipBillingService.stopRenewal(membershipId);
        if (result != MembershipBillingResult.RENEWAL_STOPPED && result != MembershipBillingResult.ALREADY_APPLIED) {
            throw new IllegalStateException("자동갱신을 끌 수 없는 이용권(C-4b " + result + "): membershipId=" + membershipId);
        }
    }

    // ---------- 판정 보조(순수) ----------

    // 갱신을 끌 수 있는 정상 유료 이용권(구독·전환으로 활성화된 행). 종료 여부와 무관
    public static boolean stoppable(BillingMembershipData membership) {
        return membership.type() == MembershipType.PAID && membership.activatedAt() != null
            && (membership.source() == MembershipSource.SUBSCRIBE
            || membership.source() == MembershipSource.TRIAL_CONVERT);
    }

    // 갱신 결제를 반영할 수 있는 이용권(정상 유료·활성·미종료)
    public static boolean renewable(BillingMembershipData membership) {
        return stoppable(membership) && membership.terminatedAt() == null;
    }

    // 저장된 회차 기간이 이용권의 현재 기간(기준일·시작 시각·종료 시각 T)에서 다시 계산한 값과 같은지
    public static boolean matches(RenewalPeriod period, BillingMembershipData membership) {
        try {
            return MembershipBillingPeriods.renewalPeriod(membership.billingAnchorDate(), membership.startAt(),
                membership.endAt()).equals(period);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    // 동의 금액 = 정책 금액, 문구 버전·동의 시각이 있다
    public static boolean consentValid(BillingMembershipData membership) {
        return membership.consent() != null && membership.consent().consentedAmount() != null
            && membership.consent().consentedAmount() == PolicyConstants.MEMBERSHIP_MONTHLY_FEE
            && StringUtils.hasText(membership.consent().consentTextVersion())
            && membership.consent().autopayConsentedAt() != null;
    }

    public static RenewalPeriod storedPeriod(MembershipPayment payment) {
        return MembershipBillingPeriods.renewalPeriodOf(payment.getGraceDeadlineAt(), payment.getPeriodStartDate(),
            payment.getPeriodEndDate());
    }

    // ---------- 내부 ----------

    // 접수 때 유효했던 READY 하나. 해지 예약만으로 버리지 않는다(D-10). 이용권이 종료됐으면 지금 실행하지 않고 만료를 기다린다
    private MembershipRenewalDecision keepValidReady(BillingMembershipData membership, MembershipPayment payment,
                                                     RenewalPeriod period, MembershipPaymentAttempt ready, Mode mode) {
        if (mode == Mode.SETTLE || membership.terminatedAt() != null) {
            return MembershipRenewalDecision.of(Kind.IN_PROGRESS, payment.getId());
        }
        if (!renewable(membership) || !matches(period, membership)) {
            return halted(payment.getId(), "MEMBERSHIP_PERIOD_MISMATCH");
        }
        return MembershipRenewalDecision.chargeReady(payment.getId(), ready.getId());
    }

    private static void cancelExpiredReady(List<MembershipPaymentAttempt> locked, LocalDateTime now) {
        for (MembershipPaymentAttempt attempt : locked) {
            if (attempt.getStatus() == MembershipPaymentAttemptStatus.READY) {
                attempt.cancelNotDispatched(now);
            }
        }
    }

    private static boolean hasPgPending(List<MembershipPaymentAttempt> locked) {
        return locked.stream().anyMatch(a -> a.getStatus() == MembershipPaymentAttemptStatus.DISPATCHING
            || a.getStatus() == MembershipPaymentAttemptStatus.UNKNOWN
            || a.getStatus() == MembershipPaymentAttemptStatus.MANUAL_REVIEW);
    }

    private static List<MembershipPaymentAttempt> validReady(List<MembershipPaymentAttempt> locked,
                                                             LocalDateTime now) {
        return locked.stream()
            .filter(a -> a.getStatus() == MembershipPaymentAttemptStatus.READY && now.isBefore(a.getLeaseExpiresAt()))
            .toList();
    }

    private static boolean isWaitingStatus(MembershipPaymentStatus status) {
        return status == MembershipPaymentStatus.PENDING || status == MembershipPaymentStatus.RETRY_WAITING
            || status == MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED;
    }

    // 종료 조건. 해지·종료 → 마감 → PG 발송 한도 순. 발송 수는 dispatchedAt이 남은 시도 수다 — 미발송으로 되돌린 시도와
    // 미발송 CANCELED는 빠지고 UNKNOWN·MANUAL_REVIEW·PAID·FAILED는 들어간다(attemptNo는 행 순번일 뿐이다)
    private String terminalReason(BillingMembershipData membership, RenewalPeriod period,
                                  List<MembershipPaymentAttempt> locked, LocalDateTime now) {
        if (membership.terminatedAt() != null) {
            return REASON_MEMBERSHIP_TERMINATED;
        }
        if (membership.cancelReservedAt() != null || !membership.autoRenew()) {
            return REASON_RENEWAL_CANCELED;
        }
        if (!now.isBefore(period.graceDeadlineAt())) {
            return REASON_DEADLINE_PASSED;
        }
        long dispatched = locked.stream().filter(a -> a.getDispatchedAt() != null).count();
        if (dispatched >= properties.renewalMaxDispatches()) {
            return REASON_ATTEMPTS_EXHAUSTED;
        }
        return null;
    }

    // 회차 FAILED + C-4b. 유예 종료 시각·종료 시각은 결제 쪽에서 바꾸지 않는다(혜택은 회원 도메인의 시각 판정으로 끝난다)
    private void closeRenewal(MembershipPayment payment, BillingMembershipData membership, String reason) {
        payment.markFailed(reason);
        stopRenewal(membership.membershipId());
        log.info("갱신 회차 종료: membershipPaymentId={}, reason={}", payment.getId(), reason);
    }

    // PENDING인데 살아 있는 시도가 없는 잔존을 재시도 대기로 바로잡는다. 마지막 시도가 미발송 CANCELED이고 그 앞도 설명 가능한
    // FAILED·미발송 CANCELED일 때만이다(시도 0건인 PENDING은 처음부터 결제해도 되는 상태로 보지 않는다).
    // 재시도 가능 시각: 앞선 FAILED가 없으면 지금, 있으면 가장 최근 FAILED 확정 + R. 취소 시각으로 다시 세지 않는다
    private boolean normalizeResidue(MembershipPayment payment, List<MembershipPaymentAttempt> locked,
                                     RenewalPeriod period, LocalDateTime now) {
        if (locked.isEmpty()) {
            return false;
        }
        MembershipPaymentAttempt last = locked.stream()
            .max(Comparator.comparingInt(MembershipPaymentAttempt::getAttemptNo)).orElseThrow();
        if (last.getStatus() != MembershipPaymentAttemptStatus.CANCELED || last.getDispatchedAt() != null) {
            return false;
        }
        boolean explained = locked.stream().allMatch(a -> a.getStatus() == MembershipPaymentAttemptStatus.FAILED
            || (a.getStatus() == MembershipPaymentAttemptStatus.CANCELED && a.getDispatchedAt() == null));
        Optional<LocalDateTime> eligibleAt = retryEligibleAt(locked, now);
        if (!explained || eligibleAt.isEmpty()) {
            return false;
        }
        LocalDateTime next = eligibleAt.get();
        payment.waitForRetry(next.isBefore(period.graceDeadlineAt()) ? next : null, REASON_NOT_DISPATCHED);
        return true;
    }

    // 실패가 없으면 now. 확정 시각이 없는 FAILED가 있으면 시각을 지어내지 않고 empty(호출자가 멈춘다)
    private Optional<LocalDateTime> retryEligibleAt(List<MembershipPaymentAttempt> locked, LocalDateTime now) {
        List<MembershipPaymentAttempt> failed = locked.stream()
            .filter(a -> a.getStatus() == MembershipPaymentAttemptStatus.FAILED).toList();
        if (failed.isEmpty()) {
            return Optional.of(now);
        }
        if (failed.stream().anyMatch(a -> a.getCompletedAt() == null)) {
            return Optional.empty();
        }
        return failed.stream().map(MembershipPaymentAttempt::getCompletedAt).max(Comparator.naturalOrder())
            .map(at -> at.plus(properties.retryInterval()));
    }

    // 살아 있는 시도 없음·종료 조건 통과 뒤. 대기 시각 → 동의·금액 → 플래그 → 재시도 가드 → 카드 순으로 보고 통과하면 같은 회차에
    // 새 READY(attemptNo = 최대 + 1, 임대 = min(now + L, G))를 저장한다. 커밋 뒤 실행은 호출자가 한다
    private MembershipRenewalDecision reissue(Long memberId, BillingMembershipData membership,
                                              MembershipPayment payment, List<MembershipPaymentAttempt> locked,
                                              RenewalPeriod period, LocalDateTime now) {
        Long paymentId = payment.getId();
        if (payment.getStatus() == MembershipPaymentStatus.RETRY_WAITING
            && (payment.getNextRetryAt() == null || now.isBefore(payment.getNextRetryAt()))) {
            return MembershipRenewalDecision.of(Kind.WAITING_RETRY, paymentId);
        }
        Optional<LocalDateTime> eligibleAt = retryEligibleAt(locked, now);
        if (eligibleAt.isEmpty()) {
            return halted(paymentId, "FAILED_WITHOUT_COMPLETED_AT");
        }
        if (now.isBefore(eligibleAt.get())
            || (payment.getNextRetryAt() != null && now.isBefore(payment.getNextRetryAt()))) {
            // 카드 대기 회차라도 앞선 실패의 24시간 간격을 앞당기지 않는다
            return MembershipRenewalDecision.of(Kind.WAITING_RETRY, paymentId);
        }
        if (!consentValid(membership) || payment.getAmount() != PolicyConstants.MEMBERSHIP_MONTHLY_FEE) {
            log.error("갱신 동의·회차 금액이 청구 조건과 달라 재청구하지 않음(운영 확인): membershipPaymentId={}", paymentId);
            return MembershipRenewalDecision.of(Kind.BLOCKED, paymentId);
        }
        if (!gate.newChargeEnabled()) {
            return MembershipRenewalDecision.of(Kind.BLOCKED, paymentId);
        }
        // 위에서 바꾼 시도·회차 상태가 가드 조회에 보이도록 먼저 내보낸다(커밋 아님)
        payments.flush();
        if (membershipPaymentQueryService.existsUnresolvedForRetry(membershipBillingService.findMembershipIds(memberId),
            paymentId)) {
            log.info("미정산 결제가 있어 갱신 재청구를 보류: membershipPaymentId={}", paymentId);
            return MembershipRenewalDecision.of(Kind.BLOCKED, paymentId);
        }
        // BK-P2: 키 상태 변경은 모두 MEMBER 잠금 아래라 이 일반 조회는 최신. 조회 예외는 잡지 않는다(카드 없음으로 단정하지 않는다)
        Optional<Long> activeKeyId = billingKeys.findIdByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE);
        if (activeKeyId.isEmpty()) {
            payment.waitForPaymentMethod();
            return MembershipRenewalDecision.of(Kind.WAITING_PAYMENT_METHOD, paymentId);
        }
        int nextNo = locked.stream().mapToInt(MembershipPaymentAttempt::getAttemptNo).max().orElse(0) + 1;
        MembershipPaymentAttempt next = attempts.saveAndFlush(MembershipPaymentAttempt.ready(payment, nextNo,
            billingKeys.getReferenceById(activeKeyId.get()), now,
            MembershipBillingPeriods.leaseUntil(now, properties.lease(), period.graceDeadlineAt())));
        payment.resumePending();
        log.info("갱신 시도 접수: membershipPaymentId={}, attemptNo={}", paymentId, nextNo);
        return MembershipRenewalDecision.chargeReady(paymentId, next.getId());
    }

    private static MembershipRenewalDecision halted(Long paymentId, String cause) {
        log.error("갱신 회차 자동 처리 중단(운영 확인): membershipPaymentId={}, cause={}", paymentId, cause);
        return MembershipRenewalDecision.of(Kind.HALTED, paymentId);
    }
}
