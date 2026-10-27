package com.recipick.domain.payment.service;

// 결제 5분 만료 처리 한 번의 결과. 결제 행에는 PaymentStatus만 남고, 이 값은 Worker 로그와 테스트에서만 쓴다.
// STALLED·MANUAL_REVIEW는 영구 종료가 아니다 — 결제가 IN_PROGRESS로 남는 한 Fallback이 1시간마다 다시 넣는다
public enum PaymentExpirationOutcome {

    EXPIRED,        // 만료 Processor가 미결제 건을 FAILED(EXPIRED)로 닫고 주문 종료·재고 복구까지 반영했다
    RESOLVED,       // PG가 PAID·FAILED를 보여 기존 확정 Processor가 승인·실패·늦은 승인 환불 요청 등으로 끝냈다
    RESCHEDULED,    // 아직 판단할 수 없어 다시 실행을 예약했다. 너무 일찍 꺼낸 작업을 만료 시각으로 미룬 경우도 포함한다
                    // Redis 예약이 실패해도 결제가 IN_PROGRESS로 남아 Fallback이 다시 등록한다
    STALLED,        // 만료 시각부터 고빈도 재조회 시간(RECHECK_WINDOW)이 지나도 판단하지 못했다. 1분 재예약을 멈추고 ERROR를 남긴다
    MANUAL_REVIEW,  // 외부 취소·주문 불일치·내부 오류처럼 자동으로 판단하면 안 되는 경우다. 재예약하지 않고 ERROR를 남긴다
    SKIPPED         // 결제가 없거나 이미 다른 경로가 끝냈거나, 잠금 아래에서 다시 보니 바꿀 것이 없었다
}
