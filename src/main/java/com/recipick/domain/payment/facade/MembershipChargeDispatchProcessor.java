package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipChargeClaim;
import com.recipick.domain.payment.service.MembershipChargeTicket;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 선점 TX. 시도 PK 한 행만 잠근다 — 회원·이용권·회차는 잠그지 않는다.
// 이 TX는 READY → DISPATCHING만 바꾸고 C-17 판정은 "미해결 → 미해결"이라 불변이다. 여러 서버가 같은 READY를 집어도
// 잠금 뒤 상태 검사로 하나만 성공한다. 성공하면 PG 호출에 필요한 값을 담아 돌려주고(빌링키 원문은 티켓 toString에서 가려진다),
// 실패하면 잠근 행에서 본 상태를 돌려준다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipChargeDispatchProcessor {

    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipBillingProperties properties;
    private final Clock clock;

    @Transactional
    public MembershipChargeClaim claim(Long attemptId) {
        MembershipPaymentAttempt attempt = attempts.findByIdForUpdate(attemptId).orElse(null);
        if (attempt == null) {
            return MembershipChargeClaim.missing();
        }
        if (!attempt.claim(LocalDateTime.now(clock))) {
            // 잠금 아래에서 본 최신 상태를 돌려준다 — 실행기가 신선한 DISPATCHING이면 GET 없이 기다리게
            return MembershipChargeClaim.rejected(attempt.getStatus(), attempt.getDispatchedAt());
        }
        MembershipPayment payment = attempt.getMembershipPayment();
        // 접수 유효 창의 끝은 회차 첫 시도의 requestedAt 기준이다(재발급 시도마다 다시 시작하지 않는다)
        LocalDateTime firstRequestedAt = attempts.findTopByMembershipPaymentIdOrderByAttemptNoAsc(payment.getId())
            .map(MembershipPaymentAttempt::getRequestedAt).orElse(attempt.getRequestedAt());
        return MembershipChargeClaim.claimed(new MembershipChargeTicket(attempt.getId(), payment.getId(),
            payment.getMembershipId(), attempt.getPortonePaymentId(), attempt.getBillingKey().getPortoneBillingKey(),
            payment.getAmount(), attempt.getDispatchedAt(),
            MembershipBillingPeriods.firstChargeAcceptUntil(firstRequestedAt, properties.firstChargeAcceptWindow())));
    }

    // 선점은 했지만 PG POST를 보내지 않은 선점자(플래그가 POST 직전에 꺼짐)만 부른다. 잠근 뒤 DB에 저장된 dispatchedAt과
    // 티켓의 값을 같은 정밀도로 비교해 같은 선점일 때만 READY로 되돌린다. 다르면(다른 선점·이미 처리됨) 강제로 되돌리지 않고
    // 그대로 둔다 — 그 시도는 일반 복구 경로(오래된 DISPATCHING 재조회)가 맡는다
    @Transactional
    public boolean releaseUnsent(MembershipChargeTicket ticket) {
        MembershipPaymentAttempt attempt = attempts.findByIdForUpdate(ticket.attemptId()).orElse(null);
        if (attempt == null) {
            return false;
        }
        boolean released = attempt.releaseUnsent(ticket.dispatchedAt());
        if (!released) {
            log.warn("미발송 선점을 되돌리지 못함(다른 선점이거나 이미 처리됨): attemptId={}, status={}",
                ticket.attemptId(), attempt.getStatus());
        }
        return released;
    }
}
