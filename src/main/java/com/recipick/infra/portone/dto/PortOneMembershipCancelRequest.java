package com.recipick.infra.portone.dto;

// 멤버십 환불용 금액 지정 취소 요청 본문(PortOne V2 POST /payments/{paymentId}/cancel).
// amount = 이번에 취소할 금액(환불액), currentCancellableAmount = 우리가 아는 현재 취소 가능 잔액(원수납액 - 이미 성공한 취소액).
// reason은 서버가 환불 행마다 만든 고정 식별값이라 조회에서 자기 취소를 알아보는 근거가 된다. 같은 행의 본문은 바뀌지 않는다.
// requester는 PortOne의 CancelRequester — 회원 즉시 해지는 CUSTOMER, 서버가 사후 반환하는 SYSTEM 환불은 ADMIN
public record PortOneMembershipCancelRequest(
    String storeId,
    Long amount,
    Long currentCancellableAmount,
    String requester,
    String reason
) {
    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";

    public PortOneMembershipCancelRequest {
        if (storeId == null || storeId.isBlank() || amount == null || amount <= 0 || currentCancellableAmount == null
            || currentCancellableAmount < amount || requester == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("금액 지정 취소 요청 값이 올바르지 않습니다");
        }
    }
}
