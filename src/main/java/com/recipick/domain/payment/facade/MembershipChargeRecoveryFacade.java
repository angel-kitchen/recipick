package com.recipick.domain.payment.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.exception.MembershipSettlementInvariantException;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor.ExpiredReadyResult;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
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

// 첫 결제 최소 복구. 플래그와 무관하게 돈다 — 이미 생긴 결제의 정합화는 멈추지 않는다(K-2).
// 대상은 잠금 없이 ID만 keyset으로 읽고, 회원 ID는 역조회 계약(findMemberIdOfMembership, 잠금 없음)으로 얻는다.
// 변경은 언제나 Processor 안에서 MEMBER부터 다시 잠그고 C-7로 소유권을 재검증한다(LC 5-1 "ID 선조회 → MEMBER부터 잠금").
// 이용권이 없다는 MEMBERSHIP-006(정상 흐름에서 불가 — 결제는 C-12로 만든 이용권만 참조)은 그 건만 건너뛰고 ERROR(ID만),
// 시도 상태는 바꾸지 않고 임의 회원을 고르지 않는다. 명시적 정합성 오류(MembershipSettlementInvariantException)만 별도 TX로 원인을
// 재검증한 뒤 자동 정합화를 멈추는 운영 표시를 남긴다(김선생 코드 검수 ④). 그 밖의 예외는 건별 격리 WARN — 다음 주기에 다시 본다.
// 비트랜잭션 진입점(Propagation.NEVER). 4단계 전체 Sweep이 이 Facade를 확장한다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipChargeRecoveryFacade {

    private final MembershipBillingProperties properties;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipChargeExecutor executor;
    private final MembershipChargeResultProcessor resultProcessor;
    private final Clock clock;

    // 처리한 시도 수
    @Transactional(propagation = Propagation.NEVER)
    public int recoverOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleBefore = now.minus(properties.lease()).minus(properties.dispatchMargin());
        LocalDateTime unknownBefore = now.minus(properties.dispatchMargin());
        int processed = 0;
        processed += sweep(page -> attempts.findStaleDispatching(staleBefore, page.afterId(), page.pageable()),
            ref -> reconcile(ref));
        processed += sweep(page -> attempts.findUnknownUpdatedBefore(unknownBefore, page.afterId(), page.pageable()),
            ref -> reconcile(ref));
        processed += sweep(page -> attempts.findExpiredReady(now, page.afterId(), page.pageable()),
            ref -> expireReady(ref, now));
        return processed;
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
                } catch (MembershipSettlementInvariantException e) {
                    // 명시적 정합성 오류(TX2 롤백, 상태 변경 없음). 별도 TX로 원인을 재검증하고 그대로면 자동 정합화를 멈춘다(v4 최종 ①).
                    // 표시 TX까지 실패하면 다음 주기에 한 번 더 같은 길을 간다
                    log.error("복구 중 정합성 오류(롤백, 운영 정합화 대상): attemptId={}, membershipPaymentId={}, membershipId={}, "
                        + "kind={}", ref.attemptId(), ref.membershipPaymentId(), ref.membershipId(), e.getKind());
                    flagInconsistent(ref, e.getKind());
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

    // 정합성 예외 뒤 운영 표시 TX(REQUIRES_NEW 아님 — 롤백된 TX2가 끝난 뒤 새로 연다). 실패는 WARN만
    private void flagInconsistent(MembershipPaymentAttemptRef ref, MembershipSettlementInvariantException.Kind kind) {
        try {
            Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
            resultProcessor.flagInconsistent(memberId, ref.attemptId(), kind, LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.warn("정합성 예외 시도의 운영 표시 실패(다음 실행에서 재시도): attemptId={}, cause={}", ref.attemptId(),
                e.getClass().getSimpleName());
        }
    }

    // 오래된 DISPATCHING·UNKNOWN: 같은 결제 ID로 재조회 → TX2. 새 ID로 POST하지 않는다(I-12)
    private void reconcile(MembershipPaymentAttemptRef ref) {
        Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
        MembershipChargeOutcome outcome = executor.reconcile(memberId, ref.attemptId());
        log.info("첫 결제 복구 재조회: attemptId={}, outcome={}", ref.attemptId(), outcome);
    }

    // READY 임대 만료: CANCELED → 재검사 → 새 시도면 바로 실행, 아니면 회차 종료(Processor가 한다)
    private void expireReady(MembershipPaymentAttemptRef ref, LocalDateTime now) {
        Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
        ExpiredReadyResult result = resultProcessor.handleExpiredReady(memberId, ref.attemptId(), now);
        log.info("첫 결제 READY 만료 처리: attemptId={}, outcome={}, nextAttemptId={}", ref.attemptId(), result.outcome(),
            result.attemptId());
        if (result.outcome() == MembershipChargeOutcome.RETRY_READY && result.attemptId() != null) {
            executor.execute(memberId, result.attemptId());
        }
    }
}
