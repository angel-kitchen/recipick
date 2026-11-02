package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import java.time.LocalDateTime;
import java.util.Optional;

// 선점 TX 결과. 성공이면 ticket, 실패면 시도 PK 잠금 아래에서 본 상태·선점 시각 — 실행기가 이 값으로 GET 여부를 정한다
// (신선한 DISPATCHING이면 GET하지 않는다). 잠금 밖의 일반 조회로 상태를 다시 읽지 않는다.
// settlementBlocked는 회차에 강한 정합성 차단 시도가 있어 거절된 선점이다 — 실행기는 임대 만료가 아니라 차단으로 끝낸다
public record MembershipChargeClaim(Optional<MembershipChargeTicket> ticket,
                                    MembershipPaymentAttemptStatus observedStatus,
                                    LocalDateTime observedDispatchedAt,
                                    boolean settlementBlocked) {

    public static MembershipChargeClaim claimed(MembershipChargeTicket ticket) {
        return new MembershipChargeClaim(Optional.of(ticket), MembershipPaymentAttemptStatus.DISPATCHING,
            ticket.dispatchedAt(), false);
    }

    public static MembershipChargeClaim rejected(MembershipPaymentAttemptStatus status, LocalDateTime dispatchedAt) {
        return new MembershipChargeClaim(Optional.empty(), status, dispatchedAt, false);
    }

    // 회차(자기 시도 포함)에 강한 차단 표시가 있어 선점하지 않음
    public static MembershipChargeClaim blocked(MembershipPaymentAttemptStatus status, LocalDateTime dispatchedAt) {
        return new MembershipChargeClaim(Optional.empty(), status, dispatchedAt, true);
    }

    // 시도 행이 없을 때. 실행기는 결제 조회 불가 예외로 바꾼다
    public static MembershipChargeClaim missing() {
        return new MembershipChargeClaim(Optional.empty(), null, null, false);
    }

    public boolean isClaimed() {
        return ticket.isPresent();
    }
}
