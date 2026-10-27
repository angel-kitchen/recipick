package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.Refund;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import java.util.Objects;

// PortOne 조회의 취소 내역을 환불 행에 연결할 때의 공통 판정 규칙. 회원 환불 재확인·웹훅 정합화·SYSTEM 환불 실행이 같은 규칙을 써야
// 같은 취소를 경로마다 다르게 판정하지 않는다. 판정만 하고 상태는 바꾸지 않는다
final class RefundCancellationRules {

    private RefundCancellationRules() {
    }

    // 레시픽 서버가 API로 요청한 전액 취소인지. 콘솔·관리자·차지백 취소는 회원 환불 행과 묶지 않는다
    static boolean isServerFullCancellation(PortOneCancellation cancellation, Refund refund) {
        return cancellation.trigger() == PortOneCancellationTrigger.API && matchesAmount(cancellation, refund);
    }

    // 전액 취소만 허용한다. 금액이 환불과 다르면 이 환불의 취소로 볼 수 없다
    static boolean matchesAmount(PortOneCancellation cancellation, Refund refund) {
        return Objects.equals(cancellation.totalAmount(), refund.getAmount());
    }

    // 고른 취소 말고 성공한 취소가 또 있으면 실제 결제는 이미 취소됐을 수 있다. 예: API 취소는 FAILED인데
    // 콘솔 취소가 SUCCEEDED면, 고른 것만 반영해 PAID로 되돌리는 순간 PortOne의 CANCELED와 어긋난다.
    // 같은 목록에서 꺼낸 객체라 참조로 비교한다. ID로 비교하면 ID가 비어 있는 취소끼리 같은 것으로 보인다
    static boolean hasOtherSucceededCancellation(PortOnePayment portOnePayment, PortOneCancellation selected) {
        return portOnePayment.cancellations().stream()
            .anyMatch(candidate -> candidate != selected && candidate.status() == PortOneCancellationStatus.SUCCEEDED);
    }

    // 콘솔·PortOne 관리자·차지백 등 API가 아닌 취소 중 실패로 끝나지 않은 것이 있는지. 처리 중·성공·알 수 없는 상태는 결과가
    // 살아 있어 결제 전체의 최종 취소 상태를 단정할 수 없다. 실패로 끝난 과거 시도는 막지 않는다.
    // SYSTEM 환불 실행(POST 전·기존 취소 반영 전)과 웹훅의 SYSTEM 환불 반영이 같은 기준으로 멈춘다
    static boolean hasActiveNonApiCancellation(PortOnePayment portOnePayment) {
        return portOnePayment.cancellations().stream()
            .anyMatch(candidate -> candidate.trigger() != PortOneCancellationTrigger.API
                && candidate.status() != PortOneCancellationStatus.FAILED);
    }
}
