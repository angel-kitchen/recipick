package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.Refund;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.util.StringUtils;

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

    // PortOne에 새 취소 POST를 보내기 전에 쓴다. 콘솔·PortOne 관리자·차지백 등 API가 아닌 취소 중 실패로 끝나지 않은 것이
    // 하나라도 있으면 다른 멱등 키의 취소를 새로 만들지 않는다. 실패로 끝난 과거 시도는 막지 않는다
    static boolean hasActiveNonApiCancellation(PortOnePayment portOnePayment) {
        return portOnePayment.cancellations().stream()
            .anyMatch(candidate -> candidate.trigger() != PortOneCancellationTrigger.API
                && candidate.status() != PortOneCancellationStatus.FAILED);
    }

    // 이미 있는 취소를 내부 상태에 반영할 때 쓴다. 지금 반영할 selected 자체는 빼고, 다른 활성 비API 취소가 있을 때만 막는다 —
    // 빼지 않으면 외부 취소를 반영하려는 순간 그 취소 때문에 스스로 막힌다
    static boolean hasOtherActiveNonApiCancellation(PortOnePayment portOnePayment, PortOneCancellation selected) {
        return portOnePayment.cancellations().stream()
            .anyMatch(candidate -> candidate != selected
                && candidate.trigger() != PortOneCancellationTrigger.API
                && candidate.status() != PortOneCancellationStatus.FAILED);
    }

    // 자동 정합화하는 외부 취소 경로. 차지백은 카드사 분쟁 절차라 운영이 판단해야 하고, 알 수 없는 경로는 근거가 없다
    static boolean isAutoReconcilableExternalTrigger(PortOneCancellationTrigger trigger) {
        return trigger == PortOneCancellationTrigger.CONSOLE || trigger == PortOneCancellationTrigger.PORTONE_ADMIN;
    }

    // 콘솔·PortOne 관리자 화면에서 끝난 전액 취소 하나를 고른다. 위에서부터 판정하고 처음 걸리는 결과를 돌려준다.
    // expectedAmount는 기존 환불이 있으면 그 환불 금액, 없으면 결제 금액이다. hintId는 웹훅이 알려 주거나 환불에 기록된 취소 ID이고 없으면 null이다.
    // 순서: 후보 선택 → 후보 자체 상태 → 금액 → 다른 취소 → PG 결제 전체 상태
    static ExternalCancellationMatch matchExternalFullCancellation(
        PortOnePayment portOnePayment,
        Long expectedAmount,
        String hintId
    ) {
        List<PortOneCancellation> cancellations = portOnePayment.cancellations();

        // 1. 후보 선택
        PortOneCancellation selected;
        if (StringUtils.hasText(hintId)) {
            Optional<PortOneCancellation> byHint = cancellations.stream()
                .filter(candidate -> hintId.equals(candidate.id()))
                .findFirst();
            if (byHint.isEmpty()) {
                // 웹훅이나 취소 응답이 PortOne 조회 결과보다 먼저 왔다
                return ExternalCancellationMatch.pending("조회 결과에 취소 내역 없음: cancellationId=" + hintId);
            }
            selected = byHint.get();
            if (selected.trigger() == PortOneCancellationTrigger.API) {
                // 정상적인 서버 취소다. 외부 취소 충돌로 보지 않고 기존 API 판정에 넘긴다.
                // 단 다른 비API 취소가 살아 있으면 "없음"으로 넘겨 숨기지 않는다
                if (hasOtherActiveNonApiCancellation(portOnePayment, selected)) {
                    return ExternalCancellationMatch.conflict(
                        "서버 API 취소와 별도의 비API 취소가 진행 중이거나 완료됨: cancellationId=" + hintId);
                }
                return ExternalCancellationMatch.absent("서버 API 취소: cancellationId=" + hintId);
            }
            if (!isAutoReconcilableExternalTrigger(selected.trigger())) {
                return ExternalCancellationMatch.conflict(
                    "자동 정합화하지 않는 취소 경로: trigger=" + selected.trigger() + ", cancellationId=" + hintId);
            }
            if (selected.status() == PortOneCancellationStatus.FAILED) {
                // 실패로 끝난 외부 취소는 결제에 남긴 영향이 없다. 단 다른 비API 취소가 살아 있으면 숨기지 않는다
                if (hasOtherActiveNonApiCancellation(portOnePayment, selected)) {
                    return ExternalCancellationMatch.conflict(
                        "실패한 외부 취소 외에 다른 비API 취소가 진행 중이거나 완료됨: cancellationId=" + hintId);
                }
                return ExternalCancellationMatch.absent("실패로 끝난 외부 취소: cancellationId=" + hintId);
            }
        } else {
            List<PortOneCancellation> candidates = cancellations.stream()
                .filter(candidate -> isAutoReconcilableExternalTrigger(candidate.trigger())
                    && candidate.status() != PortOneCancellationStatus.FAILED)
                .toList();
            if (candidates.size() > 1) {
                return ExternalCancellationMatch.conflict("살아 있는 외부 취소가 " + candidates.size() + "건");
            }
            if (candidates.isEmpty()) {
                // 콘솔·관리자 취소는 없지만 차지백·알 수 없는 취소가 살아 있으면 "없음"이 아니라 충돌이다
                return hasActiveNonApiCancellation(portOnePayment)
                    ? ExternalCancellationMatch.conflict("자동 정합화하지 않는 비API 취소가 진행 중이거나 완료됨")
                    : ExternalCancellationMatch.absent("외부 취소 없음");
            }
            selected = candidates.get(0);
        }

        // 2. 후보 자체 상태. 여기까지 온 후보는 FAILED가 아니다
        if (!StringUtils.hasText(selected.id())) {
            // ID가 없으면 환불 행에 묶을 근거가 없고 같은 취소인지도 다시 확인할 수 없다
            return ExternalCancellationMatch.conflict("외부 취소에 ID가 없음: trigger=" + selected.trigger());
        }
        if (selected.status() != PortOneCancellationStatus.SUCCEEDED) {
            return ExternalCancellationMatch.pending(
                "외부 취소 처리 중: status=" + selected.status() + ", cancellationId=" + selected.id());
        }
        if (selected.canceledAt() == null) {
            // 완료 시각은 PG가 확인한 값이어야 한다. 서버 시각으로 대신 채우지 않고 다시 묻는다
            return ExternalCancellationMatch.pending("외부 취소 완료 시각 없음: cancellationId=" + selected.id());
        }

        // 3. 금액. 전액 취소만 자동 반영한다
        if (!Objects.equals(selected.totalAmount(), expectedAmount)) {
            return ExternalCancellationMatch.conflict("외부 취소 금액 불일치: expectedAmount=" + expectedAmount
                + ", cancellationAmount=" + selected.totalAmount() + ", cancellationId=" + selected.id());
        }

        // 4. 고른 취소 외의 취소. 성공 취소가 둘이거나 다른 비API 취소가 살아 있으면 어느 것이 최종인지 단정할 수 없다
        if (hasOtherSucceededCancellation(portOnePayment, selected)) {
            return ExternalCancellationMatch.conflict("고른 취소 외에 성공한 취소가 또 있음: selected=" + selected.id());
        }
        if (hasOtherActiveNonApiCancellation(portOnePayment, selected)) {
            return ExternalCancellationMatch.conflict(
                "고른 취소 외에 API가 아닌 취소가 진행 중이거나 완료됨: selected=" + selected.id());
        }
        // 서버 API 취소가 아직 처리 중이다. 곧 FAILED나 SUCCEEDED로 정해지므로 지금 반영하지 않고 다시 묻는다.
        // FAILED로 끝난 API 취소는 결제에 남긴 영향이 없어 막지 않는다
        boolean apiInProgress = cancellations.stream()
            .anyMatch(candidate -> candidate.trigger() == PortOneCancellationTrigger.API
                && (candidate.status() == PortOneCancellationStatus.REQUESTED
                || candidate.status() == PortOneCancellationStatus.UNKNOWN));
        if (apiInProgress) {
            return ExternalCancellationMatch.pending("서버 API 취소가 처리 중: selected=" + selected.id());
        }

        // 5. PG 결제 전체 상태. 취소 내역은 성공인데 결제가 아직 PAID면 PortOne 반영 시점 차이라 다시 묻는다
        if (portOnePayment.status() == PortOnePaymentStatus.PARTIAL_CANCELED) {
            return ExternalCancellationMatch.conflict("PG 결제가 부분 취소 상태: selected=" + selected.id());
        }
        if (portOnePayment.status() != PortOnePaymentStatus.CANCELED) {
            return ExternalCancellationMatch.pending(
                "PG 결제 상태 반영 전: status=" + portOnePayment.status() + ", selected=" + selected.id());
        }

        return ExternalCancellationMatch.matched(selected);
    }
}
