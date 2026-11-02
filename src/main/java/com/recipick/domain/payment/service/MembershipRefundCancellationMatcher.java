package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// 멤버십 환불이 PG 결제 조회 결과를 읽는 순수 규칙. 세 판정을 분리한다 —
// ① 결제 식별 검증(결제 ID·상점·KRW·원수납액), ② 누적 취소액 검증(SUCCEEDED 취소의 totalAmount 합 = amount.cancelled),
// ③ 취소 소유권 매칭(저장된 취소 ID 정확 일치 → 없을 때만 서버 생성 reason 정확 일치 + API 취소 → 어느 쪽도 아니면 없음).
// 금액이 같다거나 최근 취소라는 이유로 연결하지 않는다. Repository를 보지 않고 호출자가 넘긴 값만 쓴다.
// 일시적으로 서로 다르게 보이는 조회(필드 누락·합계 불일치)는 강한 차단이 아니라 재조회 대상이므로 결과 종류를 나눠 돌려준다
public final class MembershipRefundCancellationMatcher {

    public static final String CURRENCY_KRW = "KRW";

    private MembershipRefundCancellationMatcher() {
    }

    // ① 결제 식별 검증
    public enum Identity {
        MATCH,           // 결제 ID·상점·KRW·원수납액 모두 일치
        INCOMPLETE,      // 필수 필드가 없거나 결제가 아직 끝나지 않은 상태라 비교할 수 없음 → 재조회, POST 금지
        MISMATCH,        // 존재하는 값이 저장 근거와 명백히 다름 → 운영 확인(증거 충돌)
        PAYMENT_FAILED   // 내부는 PAID인데 PG가 명백한 FAILED → 기존 수납 근거 충돌 처리, POST 금지
    }

    public static Identity identity(RefundMatchSnapshot target, PortOnePayment payment) {
        if (payment == null || payment.id() == null || target.portonePaymentId() == null) {
            return Identity.INCOMPLETE;
        }
        if (!target.portonePaymentId().equals(payment.id())) {
            return Identity.MISMATCH;
        }
        if (payment.status() == PortOnePaymentStatus.FAILED) {
            return Identity.PAYMENT_FAILED;
        }
        if (payment.status() != PortOnePaymentStatus.PAID
            && payment.status() != PortOnePaymentStatus.PARTIAL_CANCELED
            && payment.status() != PortOnePaymentStatus.CANCELED) {
            return Identity.INCOMPLETE;
        }
        if (payment.storeId() == null || payment.storeId().isBlank() || target.expectedStoreId() == null
            || payment.currency() == null || payment.currency().isBlank() || payment.totalAmount() == null
            || target.paidAmountSnapshot() == null) {
            return Identity.INCOMPLETE;
        }
        if (!target.expectedStoreId().equals(payment.storeId())
            || !CURRENCY_KRW.equals(payment.currency())
            || !target.paidAmountSnapshot().equals(payment.totalAmount())) {
            return Identity.MISMATCH;
        }
        return Identity.MATCH;
    }

    // ② 누적 취소액 검증
    public enum CancelledSum {
        CONSISTENT,     // amount.cancelled = SUCCEEDED 취소 totalAmount 합
        INCONSISTENT,   // 합이 다르거나 누적 취소액이 없음 → 재조회(일시적 불일치), POST 금지
        UNTRUSTED       // 알 수 없는 취소 상태·중복 ID·금액 누락 → 조용히 제외하지 않고 불신뢰 관측으로 처리
    }

    public static CancelledSum cancelledSum(PortOnePayment payment) {
        List<PortOneCancellation> cancellations = payment.cancellations();
        Set<String> ids = new HashSet<>();
        long succeeded = 0L;
        for (PortOneCancellation c : cancellations) {
            if (c.id() == null || c.id().isBlank() || c.status() == null
                || c.status() == PortOneCancellationStatus.UNKNOWN || c.totalAmount() == null || !ids.add(c.id())) {
                return CancelledSum.UNTRUSTED;
            }
            if (c.status() == PortOneCancellationStatus.SUCCEEDED) {
                succeeded = Math.addExact(succeeded, c.totalAmount());
            }
        }
        Long cancelled = payment.cancelledAmount();
        if (cancelled == null) {
            // 공식 PAID 응답에서 cancellations·cancelled는 선택 필드다 — 취소가 하나도 없을 때만 0으로 본다
            return cancellations.isEmpty() ? CancelledSum.CONSISTENT : CancelledSum.INCONSISTENT;
        }
        return cancelled == succeeded ? CancelledSum.CONSISTENT : CancelledSum.INCONSISTENT;
    }

    // ③ 취소 소유권 매칭
    public enum Kind {
        EXACT_ID,        // 저장된 취소 ID와 정확히 일치하는 취소 내역
        BY_REASON,       // 저장된 ID가 없고, 서버 생성 reason이 정확히 일치하는 API 취소 하나
        NONE,            // 매칭 없음(저장된 ID가 아직 조회에 보이지 않는 경우 포함)
        AMBIGUOUS,       // 같은 reason의 서로 다른 취소 ID가 여러 개
        FOREIGN_LINKED,  // 매칭된 취소가 이미 다른 환불에 귀속돼 있음
        CONTRADICTION    // 이 시도의 다른 환불(FAILED로 확정)의 취소가 SUCCEEDED로 관측됨
    }

    // unexplainedActivity: 이 시도의 어느 환불로도 설명되지 않는 REQUESTED·SUCCEEDED 취소가 있음 → 신규 POST 금지
    public record Match(Kind kind, PortOneCancellation cancellation, boolean unexplainedActivity) {
    }

    public static Match match(RefundMatchSnapshot target, PortOnePayment payment,
                              List<RefundCancellationBinding> bindings) {
        List<PortOneCancellation> cancellations = payment.cancellations();
        List<RefundCancellationBinding> others = bindings.stream()
            .filter(b -> !Objects.equals(b.refundId(), target.refundId()))
            .toList();
        for (RefundCancellationBinding other : others) {
            if (other.status() == MembershipRefundStatus.FAILED && other.portoneCancellationId() != null) {
                PortOneCancellation seen = byId(cancellations, other.portoneCancellationId());
                if (seen != null && seen.status() != PortOneCancellationStatus.FAILED) {
                    return new Match(Kind.CONTRADICTION, seen, true);
                }
            }
        }
        Kind kind;
        PortOneCancellation matched;
        List<PortOneCancellation> candidates = target.pgRequestReason() == null ? List.of() : cancellations.stream()
                                                                                              .filter(c -> c.trigger() == PortOneCancellationTrigger.API)
                                                                                              .filter(c -> target.pgRequestReason().equals(c.reason()))
                                                                                              .toList();
        if (target.portoneCancellationId() != null) {
            matched = byId(cancellations, target.portoneCancellationId());
            boolean sameReasonOtherId = candidates.stream()
                .anyMatch(c -> !target.portoneCancellationId().equals(c.id()));
            if (sameReasonOtherId) {
                // 저장된 ID 외에 같은 reason의 다른 취소가 있다 — 같은 요청이 두 번 취소됐을 수 있어 자동으로 고르지 않는다
                kind = Kind.AMBIGUOUS;
                matched = matched != null ? matched : candidates.get(0);
            } else if (matched == null) {
                kind = Kind.NONE;
            } else if (boundToOther(others, matched.id())) {
                kind = Kind.FOREIGN_LINKED;
            } else {
                kind = Kind.EXACT_ID;
            }
        } else {
            if (candidates.size() > 1) {
                kind = Kind.AMBIGUOUS;
                matched = candidates.get(0);
            } else if (candidates.size() == 1) {
                matched = candidates.get(0);
                kind = boundToOther(others, matched.id()) ? Kind.FOREIGN_LINKED : Kind.BY_REASON;
            } else {
                kind = Kind.NONE;
                matched = null;
            }
        }
        boolean unexplained = false;
        for (PortOneCancellation c : cancellations) {
            if (c.status() != PortOneCancellationStatus.REQUESTED && c.status() != PortOneCancellationStatus.SUCCEEDED) {
                continue;
            }
            if (matched != null && Objects.equals(matched.id(), c.id())) {
                continue;
            }
            if (!explainedBy(bindings, c)) {
                unexplained = true;
            }
        }
        return new Match(kind, matched, unexplained);
    }

    private static PortOneCancellation byId(List<PortOneCancellation> cancellations, String id) {
        if (id == null) {
            return null;
        }
        return cancellations.stream().filter(c -> id.equals(c.id())).findFirst().orElse(null);
    }

    private static boolean boundToOther(List<RefundCancellationBinding> others, String cancellationId) {
        return cancellationId != null
            && others.stream().anyMatch(b -> cancellationId.equals(b.portoneCancellationId()));
    }

    // 어느 환불 행이든 저장된 취소 ID가 같거나, API 취소이고 그 행의 서버 생성 reason이 같으면 설명된 취소다
    private static boolean explainedBy(List<RefundCancellationBinding> bindings, PortOneCancellation c) {
        for (RefundCancellationBinding b : bindings) {
            if (c.id() != null && c.id().equals(b.portoneCancellationId())) {
                return true;
            }
            if (c.trigger() == PortOneCancellationTrigger.API && b.pgRequestReason() != null
                && b.pgRequestReason().equals(c.reason())) {
                return true;
            }
        }
        return false;
    }
}
