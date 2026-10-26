package com.recipick.domain.payment.entity.enums;

// // 웹훅 inbox 상태. RECEIVED → PROCESSING → (PROCESSED | IGNORED | MANUAL_REVIEW 종료) 또는 RETRY_PENDING(재시도 대기)
public enum WebhookEventStatus {
    RECEIVED, // 저장만 됐고 아직 Worker가 잡지 않았다
    PROCESSING, // Worker가 잡아 처리중. updated_at이 오래되면 멈춘 것으로 보고 다시 잡는다
    PROCESSED, // 결제·환불 상태에 반영을 끝냈다(이미 반영돼 있어 할 일이 없던 경우 포함)
    IGNORED, // 처리 대상이 아니다 ㅡ 모르는 type, 다른 storeId, 우리 결제가 아님
    RETRY_PENDING, // PortOne 조회 실패 같은 일시 장애. 큐가 backoff 뒤 다시 잡는다
    MANUAL_REVIEW; // 자동으로 판단할 수 없어 사람이 봐야 한다 ㅡ 부분 취소, 기록 없는 취소, 최대 시도 초과

    // 종료 상태면 큐에 같은 이벤트가 다시 와도 건너뛴다
    public boolean isFinished() {
        return this == PROCESSED || this == IGNORED || this == MANUAL_REVIEW;
    }
}
