package com.recipick.domain.payment.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor.ExpiredReadyResult;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.service.BillingKeyRecheckRef;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 멤버십 결제 시도 복구(첫 결제·갱신 공통). 플래그와 무관하게 돈다 — 이미 생긴 결제의 정합화는 멈추지 않는다.
// 세 조회는 회차 종류를 가리지 않는다. 갱신 시도의 READY 만료도 같은 handleExpiredReady가 갱신 규칙으로 처리한다.
// 대상은 잠금 없이 ID만 keyset으로 읽고, 회원 ID는 역조회 계약(findMemberIdOfMembership, 잠금 없음)으로 얻는다.
// 변경은 언제나 Processor 안에서 MEMBER부터 다시 잠그고 C-7로 소유권을 재검증한다(LC 5-1 "ID 선조회 → MEMBER부터 잠금").
// 이용권이 없다는 MEMBERSHIP-006(정상 흐름에서 불가 — 결제는 C-12로 만든 이용권만 참조)은 그 건만 건너뛰고 ERROR(ID만),
// 시도 상태는 바꾸지 않고 임의 회원을 고르지 않는다. 명시적 정합성 오류는 실행기가 잡아 강한 차단 표시를 남긴다.
// 강한 차단 시도가 있는 회차는 세 조회에서 모두 빠진다. 그 밖의 예외는 건별 격리 WARN — 다음 주기에 다시 본다.
// 비트랜잭션 진입점(Propagation.NEVER)
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipChargeRecoveryFacade {

    private static final Collection<MembershipOpsReviewReason> BLOCKING = MembershipOpsReviewReason.BLOCKING;

    private final MembershipBillingProperties properties;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipChargeExecutor executor;
    private final MembershipChargeResultProcessor resultProcessor;
    private final BillingKeyReconciliationFacade billingKeyReconciliation;
    private final Clock clock;

    // 처리한 시도 수
    @Transactional(propagation = Propagation.NEVER)
    public int recoverOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleBefore = now.minus(properties.lease()).minus(properties.dispatchMargin());
        LocalDateTime unknownBefore = now.minus(properties.dispatchMargin());
        int processed = 0;
        processed += sweep(page -> attempts.findStaleDispatching(staleBefore, BLOCKING, page.afterId(),
                page.pageable()),
            ref -> reconcile(ref));
        processed += sweep(page -> attempts.findUnknownUpdatedBefore(unknownBefore, BLOCKING, page.afterId(),
                page.pageable()),
            ref -> reconcile(ref));
        processed += sweep(page -> attempts.findExpiredReady(now, BLOCKING, page.afterId(), page.pageable()),
            ref -> expireReady(ref, now));
        processed += recheckBillingKeys(now.minus(properties.unknownWindow()));
        return processed;
    }

    // 결과 불명 창 안의 404 UNKNOWN 시도에 묶인 키를 한 번씩 확인한다(키 없음·삭제로 결제가 만들어지지 않았을 수 있다).
    // 키 상태만 바꾸고 결제 시도는 그대로 둔다(같은 결제 ID 재조회 계속). 건별 격리
    private int recheckBillingKeys(LocalDateTime since) {
        int processed = 0;
        long afterId = 0;
        Pageable pageable = PageRequest.of(0, properties.batchSize());
        while (true) {
            List<BillingKeyRecheckRef> refs = attempts.findBillingKeysToRecheck(since, afterId, pageable);
            for (BillingKeyRecheckRef ref : refs) {
                processed++;
                try {
                    billingKeyReconciliation.recheck(ref);
                } catch (RuntimeException e) {
                    log.warn("빌링키 재검사 실패(다음 실행에서 재시도): billingKeyId={}, cause={}", ref.billingKeyId(),
                        e.getClass().getSimpleName());
                }
            }
            if (refs.size() < properties.batchSize()) {
                return processed;
            }
            afterId = refs.get(refs.size() - 1).billingKeyId();
        }
    }

    private record Page(long afterId, Pageable pageable) {
    }

    private int sweep(Function<Page, List<MembershipPaymentAttemptRef>> query,
                      Consumer<MembershipPaymentAttemptRef> action) {
        int processed = 0;
        long afterId = 0;
        Pageable pageable = PageRequest.of(0, properties.batchSize());
        while (true) {
            List<MembershipPaymentAttemptRef> refs = query.apply(new Page(afterId, pageable));
            for (MembershipPaymentAttemptRef ref : refs) {
                processed++;
                try {
                    action.accept(ref);
                } catch (BusinessException e) {
                    if (e.getErrorCode() == MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND) {
                        log.error("복구 대상 시도의 이용권을 찾을 수 없어 건너뜀(운영 확인): attemptId={}, "
                                + "membershipPaymentId={}, membershipId={}", ref.attemptId(), ref.membershipPaymentId(),
                            ref.membershipId());
                    } else {
                        log.warn("복구 처리 실패(다음 실행에서 재시도): attemptId={}, code={}", ref.attemptId(),
                            e.getErrorCode().getCode());
                    }
                } catch (RuntimeException e) {
                    log.warn("복구 처리 실패(다음 실행에서 재시도): attemptId={}, cause={}", ref.attemptId(),
                        e.getClass().getSimpleName());
                }
            }
            if (refs.size() < properties.batchSize()) {
                return processed;
            }
            afterId = refs.get(refs.size() - 1).attemptId();
        }
    }

    // 오래된 DISPATCHING·UNKNOWN: 같은 결제 ID로 재조회 → TX2. 새 ID로 POST하지 않는다(I-12)
    private void reconcile(MembershipPaymentAttemptRef ref) {
        Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
        MembershipChargeOutcome outcome = executor.reconcile(memberId, ref.attemptId());
        log.info("멤버십 결제 복구 재조회: attemptId={}, outcome={}", ref.attemptId(), outcome);
    }

    // READY 임대 만료: CANCELED → 재검사 → 새 시도면 바로 실행, 아니면 회차 종료(Processor가 한다)
    private void expireReady(MembershipPaymentAttemptRef ref, LocalDateTime now) {
        Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
        ExpiredReadyResult result = resultProcessor.handleExpiredReady(memberId, ref.attemptId(), now);
        log.info("멤버십 결제 READY 만료 처리: attemptId={}, outcome={}, nextAttemptId={}", ref.attemptId(), result.outcome(),
            result.attemptId());
        if (result.outcome() == MembershipChargeOutcome.RETRY_READY && result.attemptId() != null) {
            executor.execute(memberId, result.attemptId());
        }
    }
}
