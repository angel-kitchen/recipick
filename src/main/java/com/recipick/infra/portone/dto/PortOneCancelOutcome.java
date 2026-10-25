package com.recipick.infra.portone.dto;

// PortOne이 취소 요청에 무엇이라 답했는지. 결제·주문 상태를 어떻게 바꿀지는 domain이 이 값을 보고 정한다.
public enum PortOneCancelOutcome {

    // cancellation.status = SUCCEEDED
    SUCCEEDED,

    //cancellation.status = REQUESTED. PG가 비동기로 처리 중이다
    REQUESTED,

    //cancellation.status = FAILED. PortOne이 취소 실패를 명시했다
    FAILED,

    // 400·401·403. PortOne이 요청을 처리하기 전에 거절했다 (우리 요청·인증 설정 문제)
    REJECTED,

    // 404, 상태 불일치 409. PortOne과 우리가 아는 결제 상태가 어긋난다
    MISMATCH,

    // timeout·연결 실패·5xx·처리 중 409·해석 불가 응답. 취소됐는지 알 수 없다
    UNCONFIRMED
}
