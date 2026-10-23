package com.recipick.infra.portone.dto;

// PortOne 결제 취소 요청 본문.
// amount를 보내지 않아 전액 취소하고, 회원 취소는 requester=CUSTOMER로 보낸다.
public record PortOneCancelRequest(
    String reason,
    Long currentCancellableAmount,
    String requester
) {
    private static final String CUSTOMER = "CUSTOMER";

    public static PortOneCancelRequest byCustomer(String reason, Long currentCancellableAmount) {
        return new PortOneCancelRequest(reason, currentCancellableAmount, CUSTOMER);
    }
}
