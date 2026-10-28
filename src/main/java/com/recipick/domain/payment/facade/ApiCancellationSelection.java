package com.recipick.domain.payment.facade;

import com.recipick.infra.portone.dto.PortOneCancellation;

// 취소 ID를 아직 못 받은 마트 거절 환불이 PortOne의 서버 API 취소 중 자기 것을 고른 결과. 실행기·웹훅이 같은 판정을 쓰고,
// 고를 것이 없을 때(NONE)의 행동만 호출자가 정한다 — 실행기는 POST하고 웹훅은 POST하지 않고 다시 묻는다.
// NONE과 AMBIGUOUS를 나누는 이유: 없다(새로 요청해도 안전)와 있지만 고를 수 없다(새로 요청하면 이중 취소 위험)는 반대 결정이다
record ApiCancellationSelection(Type type, PortOneCancellation cancellation, String reason) {

    enum Type {
        SELECTED,  // 이 환불의 취소로 볼 수 있는 것이 정확히 하나다
        NONE,      // 이 환불의 취소가 아직 없다. 남은 서버 API 취소는 실패로 끝난 과거 시도뿐이다
        AMBIGUOUS  // 이 환불의 것이라고 확신할 수 없는 취소가 살아 있다. 고르지도 새로 요청하지도 않고 운영에 넘긴다
    }

    static ApiCancellationSelection selected(PortOneCancellation cancellation) {
        return new ApiCancellationSelection(Type.SELECTED, cancellation, null);
    }

    static ApiCancellationSelection none() {
        return new ApiCancellationSelection(Type.NONE, null, "이 환불 이후의 서버 API 취소 없음");
    }

    static ApiCancellationSelection ambiguous(String reason) {
        return new ApiCancellationSelection(Type.AMBIGUOUS, null, reason);
    }
}
