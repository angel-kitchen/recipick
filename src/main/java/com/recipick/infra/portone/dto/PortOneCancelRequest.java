package com.recipick.infra.portone.dto;

// PortOne 결제 취소 요청 본문. amount를 보내지 않아 전액 취소다
// requester는 PortOne V2의 CancelRequester(CUSTOMER·ADMIN). 회원 취소는 CUSTOMER, 서버가 사후 반환하는 SYSTEM 환불은 ADMIN이다
public record PortOneCancelRequest(
    String reason,
    Long currentCancellableAmount,
    String requester
) {
    private static final String CUSTOMER = "CUSTOMER";
    private static final String ADMIN = "ADMIN";

    public static PortOneCancelRequest byCustomer(String reason, Long currentCancellableAmount) {
        return new PortOneCancelRequest(reason, currentCancellableAmount, CUSTOMER);
    }

    // SYSTEM 환불(만료 뒤 승인·금액 불일치·늦은 승인)의 사후 반환. 고객이 요청한 취소가 아니라 ADMIN으로 보낸다
    public static PortOneCancelRequest byAdmin(String reason, Long currentCancellableAmount) {
        return new PortOneCancelRequest(reason, currentCancellableAmount, ADMIN);
    }
}
