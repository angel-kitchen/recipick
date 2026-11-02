package com.recipick.domain.payment.service;

import com.recipick.infra.portone.dto.PortOnePayment;
import java.time.LocalDateTime;

// 같은 결제 ID 재조회(GET /payments/{id}) 결과를 회차 금액·통화와 대조해 판정한다. 호출 결과(POST)의 outcome은 쓰지 않는다.
// 모든 분류는 먼저 PortOne이 돌려준 결제 ID가 우리 결제 ID와 같은지 본다 — 다르면 FAILED여도 믿지 않는다(UNTRUSTED).
// paidAmount는 신뢰 가능한 PAID일 때 PG의 실제 수납액(totalAmount). 그 외 null. code는 고정 코드만(PG 자유 문장 failureSummary는 쓰지 않는다).
// pgRecordObserved는 "PG가 이 결제 ID의 거래 객체를 돌려줬는가"다 — 404·조회 장애·빈 응답은 false, 객체가 있으면 ID가 달라도 true.
// 거래가 존재하지만 아직 끝나지 않은 상황(PENDING 등)과 조회 자체가 실패한 상황은 같은 뜻이 아니라서 kind와 별도로 둔다.
// 객체의 존재만 믿고 그 금액·수납 상태는 kind가 신뢰 가능하다고 말할 때만 쓴다
public record MembershipPgVerdict(Kind kind, LocalDateTime pgPaidAt, Long paidAmount, String code,
                                  boolean pgRecordObserved) {

    public static final String CURRENCY_KRW = "KRW";
    public static final String CODE_PG_FAILED = "PG_FAILED";
    public static final String CODE_AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String CODE_NOT_FOUND = "PAYMENT_NOT_FOUND";
    public static final String CODE_UNTRUSTED_ID = "UNTRUSTED_ID";
    public static final String CODE_UNTRUSTED_FIELD = "UNTRUSTED_FIELD";
    public static final String CODE_UNTRUSTED_CURRENCY = "UNTRUSTED_CURRENCY";
    public static final String CODE_UNTRUSTED_CANCELED = "UNTRUSTED_CANCELED";
    public static final String CODE_UNTRUSTED_STATUS = "UNTRUSTED_STATUS";
    public static final String CODE_EMPTY = "EMPTY_RESPONSE";

    public enum Kind {
        // ID·상태 PAID·paidAt·금액·통화 모두 확인, 금액 = 청구액, 통화 KRW
        PAID,
        // 신뢰 가능한 PAID인데 KRW 수납액이 청구액과 다름 → 반영 안 함 + 실제 수납액 SYSTEM 환불
        PAID_AMOUNT_MISMATCH,
        // 재조회로 확정된 실패(ID 일치)
        FAILED,
        // 진행 중 상태(거래 객체 있음) 또는 조회 오류·timeout(거래 객체 없음) → 시도 UNKNOWN, 같은 ID 재조회
        UNRESOLVED,
        // 404 → PG에 이 ID의 거래가 없음이 확인됨. UNKNOWN 유지, 새 ID 금지
        NOT_FOUND,
        // ID 불일치·필수값 누락·KRW 아님·예상 밖 취소 상태·해석 불가 상태 → MANUAL_REVIEW, 환불 금액을 만들지 않음
        UNTRUSTED
    }

    public static MembershipPgVerdict of(String expectedPaymentId, long expectedAmount, PortOnePayment payment) {
        if (payment == null) {
            return unresolved(CODE_EMPTY);
        }
        if (expectedPaymentId == null || !expectedPaymentId.equals(payment.id())) {
            return observedUntrusted(CODE_UNTRUSTED_ID);
        }
        if (payment.status() == null) {
            return observedUntrusted(CODE_UNTRUSTED_STATUS);
        }
        return switch (payment.status()) {
            case PAID -> classifyPaid(expectedAmount, payment);
            case FAILED -> new MembershipPgVerdict(Kind.FAILED, null, null, CODE_PG_FAILED, true);
            case CANCELED, PARTIAL_CANCELED -> observedUntrusted(CODE_UNTRUSTED_CANCELED);
            case READY, PENDING, VIRTUAL_ACCOUNT_ISSUED ->
                new MembershipPgVerdict(Kind.UNRESOLVED, null, null, "PG_" + payment.status().name(), true);
            case UNKNOWN -> observedUntrusted(CODE_UNTRUSTED_STATUS);
        };
    }

    private static MembershipPgVerdict classifyPaid(long expectedAmount, PortOnePayment payment) {
        if (payment.paidAt() == null || payment.totalAmount() == null || payment.totalAmount() <= 0
            || payment.currency() == null || payment.currency().isBlank()) {
            return observedUntrusted(CODE_UNTRUSTED_FIELD);
        }
        if (!CURRENCY_KRW.equals(payment.currency())) {
            return observedUntrusted(CODE_UNTRUSTED_CURRENCY);
        }
        if (payment.totalAmount() != expectedAmount) {
            return new MembershipPgVerdict(Kind.PAID_AMOUNT_MISMATCH, payment.paidAt(), payment.totalAmount(),
                CODE_AMOUNT_MISMATCH, true);
        }
        return new MembershipPgVerdict(Kind.PAID, payment.paidAt(), payment.totalAmount(), null, true);
    }

    // HTTP 404 — PG에 이 결제 ID의 거래가 없다
    public static MembershipPgVerdict notFound() {
        return new MembershipPgVerdict(Kind.NOT_FOUND, null, null, CODE_NOT_FOUND, false);
    }

    // 조회 자체가 실패(COMMON-008·COMMON-009 등 오류 코드만) 또는 빈 응답. 거래의 존재도 부재도 증명하지 못한다
    public static MembershipPgVerdict unresolved(String code) {
        return new MembershipPgVerdict(Kind.UNRESOLVED, null, null, code, false);
    }

    // 거래 객체는 받았지만 믿을 수 없는 내용
    private static MembershipPgVerdict observedUntrusted(String code) {
        return new MembershipPgVerdict(Kind.UNTRUSTED, null, null, code, true);
    }

    // 신뢰 가능한 PAID(금액 일치 여부와 무관). 실제 수납액으로 SYSTEM 환불을 만들 수 있는 결과
    public boolean isTrustedPaid() {
        return kind == Kind.PAID || kind == Kind.PAID_AMOUNT_MISMATCH;
    }

    // PG에 이 결제 ID의 거래가 없음이 확인됐다(404). 조회 장애(UNRESOLVED·객체 없음)는 부재 확인이 아니다
    public boolean confirmsNoPgRecord() {
        return kind == Kind.NOT_FOUND;
    }
}
