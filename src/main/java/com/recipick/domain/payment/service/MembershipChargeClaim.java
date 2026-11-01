package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import java.time.LocalDateTime;
import java.util.Optional;

// 선점 TX 결과. 성공이면 ticket, 실패면 시도 PK 잠금 아래에서 본 상태·선점 시각 — 실행기가 이 값으로 GET 여부를 정한다
// (신선한 DISPATCHING이면 GET하지 않는다). 잠금 밖의 일반 조회로 상태를 다시 읽지 않는다
public record MembershipChargeClaim(Optional<MembershipChargeTicket> ticket,
                                    MembershipPaymentAttemptStatus observedStatus,
                                    LocalDateTime observedDispatchedAt) {

    public static MembershipChargeClaim claimed(MembershipChargeTicket ticket) {
        return new MembershipChargeClaim(Optional.of(ticket), MembershipPaymentAttemptStatus.DISPATCHING,
            ticket.dispatchedAt());
    }

    public static MembershipChargeClaim rejected(MembershipPaymentAttemptStatus status, LocalDateTime dispatchedAt) {
        return new MembershipChargeClaim(Optional.empty(), status, dispatchedAt);
    }

    // 시도 행이 없을 때. 실행기는 결제 조회 불가 예외로 바꾼다
    public static MembershipChargeClaim missing() {
        return new MembershipChargeClaim(Optional.empty(), null, null);
    }

    public boolean isClaimed() {
        return ticket.isPresent();
    }
}
