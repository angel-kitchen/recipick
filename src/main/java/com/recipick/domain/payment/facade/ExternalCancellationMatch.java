package com.recipick.domain.payment.facade;

import com.recipick.infra.portone.dto.PortOneCancellation;

// 외부 전액 취소 판정 결과. 웹훅·회원 환불 재확인·SYSTEM 환불 실행이 같은 결과로 같은 결정을 내리게 하려고 종류를 넷으로 나눈다.
// ABSENT(자동 정합화할 외부 취소가 없음)와 CONFLICT(외부 취소는 있는데 자동으로 고르면 위험함)를 섞으면
// 재확인 경로가 실제 이상 상태를 몇 시간씩 재시도만 하다 놓친다
record ExternalCancellationMatch(Type type, PortOneCancellation cancellation, String reason) {

    enum Type {
        MATCHED,   // 안전한 외부 전액 취소 하나를 골랐다. 자동 반영한다
        PENDING,   // 외부 취소 근거는 있지만 아직 확정할 수 없다. 다시 묻는다
        ABSENT,    // 자동 정합화할 외부 취소가 없다. 기존 API 취소 판정을 이어 간다
        CONFLICT   // 외부 취소 근거가 있지만 자동으로 고르면 위험하다. 운영에 넘긴다
    }

    static ExternalCancellationMatch matched(PortOneCancellation cancellation) {
        return new ExternalCancellationMatch(Type.MATCHED, cancellation, null);
    }

    static ExternalCancellationMatch pending(String reason) {
        return new ExternalCancellationMatch(Type.PENDING, null, reason);
    }

    static ExternalCancellationMatch absent(String reason) {
        return new ExternalCancellationMatch(Type.ABSENT, null, reason);
    }

    static ExternalCancellationMatch conflict(String reason) {
        return new ExternalCancellationMatch(Type.CONFLICT, null, reason);
    }
}
