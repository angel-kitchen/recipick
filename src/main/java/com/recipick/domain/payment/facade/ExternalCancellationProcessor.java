package com.recipick.domain.payment.facade;

import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.service.ExternalCancellationResult;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.payment.service.PointRestoreOutcome;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// PortOne 콘솔·관리자 화면에서 끝난 전액 취소를 결제·환불·주문·포인트·재고에 반영한다. 외부 취소로 상태를 바꾸는 곳은 여기뿐이다.
// PortOne 조회와 외부 취소 판정(RefundCancellationRules.matchExternalFullCancellation)은 호출한 쪽이 트랜잭션 밖에서 끝낸 뒤 부른다.
// 기존 회원·SYSTEM·마트 거절 환불로 마무리돼야 하는 취소는 새 EXTERNAL 환불을 만들지 않고 RETRY·NOT_APPLICABLE로 돌려준다.
// 마트 거절 환불이 실패로 끝난 뒤의 콘솔 전액 취소는 예외다 — 1차에는 재처리 API가 없어 운영자가 콘솔에서 돌려주고 여기서 거절 주문을 닫는다.
// 업무 결과는 예외가 아니라 반환값이다 — 안에서 던지면 롤백돼 버린다. 판정 규칙에 맞지 않는 취소를 넘긴 것은 호출자 버그라 예외로 막는다
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalCancellationProcessor {

    private final PaymentService paymentService;
    private final OrderFacade orderFacade;
    private final PointCommandService pointCommandService;

    // 락 순서 PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고. 회원 취소·결제 확정·만료·SYSTEM 환불과 같은
    // PAYMENT 잠금을 잡으므로 먼저 잡은 쪽만 상태를 바꾼다. 결제 CANCELED·EXTERNAL 환불·주문 REFUNDED·포인트·재고 복구는
    // 한 트랜잭션이다. 하나라도 실패하면 모두 롤백된다
    @Transactional
    public ExternalCancellationResult apply(Long paymentId, PortOneCancellation cancellation) {
        Payment payment = paymentService.getByIdForUpdate(paymentId);
        requireCompletedExternalCancellation(cancellation);

        // 같은 취소가 이미 어느 환불에 묶여 있으면 그 환불의 상태로 끝낸다. 웹훅 재전송·동시 처리의 멱등 기준이다
        Optional<Refund> sameCancellation = paymentService.findRefundByPortoneCancellationId(cancellation.id());
        if (sameCancellation.isPresent()) {
            Refund refund = sameCancellation.get();
            if (!refund.getPayment().getId().equals(payment.getId())) {
                return notApplicable(payment, "PortOne 취소 ID가 다른 결제의 환불에 연결됨: refundId=" + refund.getId());
            }
            // 기존 환불이 대표하는 PG 취소 금액은 환불 금액이다. 금액 불일치로 생긴 SYSTEM 환불은 결제 금액과 다르므로
            // 결제 금액으로 비교하면 같은 웹훅의 재전송이 멱등 성공이 아니라 오류가 된다
            requireAmount(cancellation.totalAmount(), refund.getAmount());
            return judgeSameCancellation(payment, refund);
        }

        // 새 EXTERNAL 환불은 결제 전액을 돌려준 취소에만 만든다
        requireAmount(cancellation.totalAmount(), payment.getAmount());

        // 판정과 잠금 사이에 회원 취소·마트 거절이 새 환불을 만들었을 수 있다. 기존 환불이 우선이라 EXTERNAL을 만들지 않는다
        List<Refund> requested = new ArrayList<>(paymentService.findRequestedMemberRefunds(paymentId));
        paymentService.findRequestedSystemRefund(paymentId).ifPresent(requested::add);
        requested.addAll(paymentService.findRequestedAdminRefunds(paymentId));
        if (!requested.isEmpty()) {
            return judgeRequested(payment, requested);
        }

        // 결제 전·실패·취소 진행 중·이미 취소된 결제는 새 EXTERNAL 환불의 대상이 아니다
        if (payment.getStatus() != PaymentStatus.PAID) {
            return notApplicable(payment, "외부 취소 자동 반영 대상 결제가 PAID가 아님: status=" + payment.getStatus());
        }

        // 마트 거절 환불이 실패로 끝난 주문인지 상태를 바꾸기 전에 본다. 1차에는 재처리가 없어 실패한 마트 거절 환불은 결제당 최대 한 건이다.
        // 둘 이상이면 어느 거절의 복구인지 단정할 수 없어 아무것도 바꾸지 않는다
        List<Refund> failedAdmin = paymentService.findFailedAdminRefunds(paymentId);
        if (failedAdmin.size() > 1) {
            return notApplicable(payment, "실패한 마트 거절 환불이 여러 건: " + failedAdmin.size());
        }

        String reason = "PortOne 외부 전액 취소(" + cancellation.trigger() + ")";
        // 요청 시각이 없으면 PG가 확인한 완료 시각을 쓴다. 서버 시각으로 대신 채우지 않는다
        LocalDateTime requestedAt = cancellation.requestedAt() != null
            ? cancellation.requestedAt()
            : cancellation.canceledAt();
        payment.markExternallyCanceled(cancellation.canceledAt());
        paymentService.recordExternalRefund(payment, cancellation.id(), reason, requestedAt, cancellation.canceledAt());
        if (failedAdmin.size() == 1) {
            // 마트 거절 뒤 PG 취소가 실패해 결제만 PAID로 돌아간 주문이다. 주문은 이미 CANCEL_REQUESTED·마트 REJECTED라
            // 외부 취소 종료(PAID → REFUNDED)가 아니라 환불 완료 경로로 닫는다. 실패한 마트 거절 환불은 시도 이력으로 그대로 남긴다
            List<StockQuantity> items = orderFacade.markRefunded(payment.getOrderId());
            reconcilePoint(payment);
            orderFacade.restoreStock(items);
            log.info("실패한 마트 거절 뒤 PortOne 외부 전액 취소로 거절 주문 종료: paymentId={}, orderId={}, failedRefundId={}, "
                    + "cancellationId={}",
                paymentId, payment.getOrderId(), failedAdmin.get(0).getId(), cancellation.id());
            return ExternalCancellationResult.reconciled();
        }
        // 배송이 시작된 주문이면 빈 목록이 와서 재고를 건드리지 않는다. 실물은 관리자가 재고 조정으로 맞춘다
        List<StockQuantity> items = orderFacade.closeByExternalCancel(payment.getOrderId(), reason);
        reconcilePoint(payment);
        orderFacade.restoreStock(items);
        log.info("PortOne 외부 전액 취소 반영: paymentId={}, orderId={}, cancellationId={}, trigger={}",
            paymentId, payment.getOrderId(), cancellation.id(), cancellation.trigger());
        return ExternalCancellationResult.reconciled();
    }

    // PG에서 이미 끝난 취소라 포인트 때문에 결제·주문 정합화를 롤백하지 않는다. 적립 상태 판정은 포인트 쪽이 잠근 뒤에 한다.
    // 배송완료로 적립이 확정된 주문은 사용 포인트도 적립도 자동으로 회수하지 않고, 운영이 확인하도록 ERROR로 남긴다
    private void reconcilePoint(Payment payment) {
        PointRestoreOutcome outcome = pointCommandService.restoreForExternalCancel(payment.getOrderId());
        if (outcome == PointRestoreOutcome.FINALIZED_EARNING_KEPT) {
            log.error("적립이 확정된 주문의 외부 전액 취소 — 포인트 자동 회수 없음, 운영 확인 필요: paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId());
        }
    }

    // 판정 규칙을 통과한 완료 외부 취소만 받는다. 아니면 PG에서 확정되지 않은 취소로 돈과 주문을 바꾸게 되므로 호출자 버그로 막는다.
    // 금액은 기준이 경로마다 달라(기존 환불 금액·결제 금액) 여기서 보지 않는다
    private static void requireCompletedExternalCancellation(PortOneCancellation cancellation) {
        if (!RefundCancellationRules.isAutoReconcilableExternalTrigger(cancellation.trigger())) {
            throw new IllegalStateException("자동 반영 대상 외부 취소가 아닙니다: trigger=" + cancellation.trigger());
        }
        if (cancellation.status() != PortOneCancellationStatus.SUCCEEDED) {
            throw new IllegalStateException("완료된 외부 취소가 아닙니다: status=" + cancellation.status());
        }
        if (!StringUtils.hasText(cancellation.id())) {
            throw new IllegalStateException("외부 취소 ID가 없습니다: trigger=" + cancellation.trigger());
        }
        if (cancellation.canceledAt() == null) {
            throw new IllegalStateException("외부 취소 완료 시각이 없습니다: cancellationId=" + cancellation.id());
        }
    }

    // 전액 취소만 반영한다. Long은 참조 비교가 되지 않게 Objects.equals로 보고, 금액이 비어 있어도 같은 예외로 막는다
    private static void requireAmount(Long cancellationAmount, Long expectedAmount) {
        if (!Objects.equals(cancellationAmount, expectedAmount)) {
            throw new IllegalStateException("외부 취소 금액이 기준 금액과 다릅니다: expected=" + expectedAmount
                + ", cancellation=" + cancellationAmount);
        }
    }

    // 같은 취소 ID의 환불이 있다. 결제 상태·요청 주체가 정상 조합일 때만 멱등 성공으로 본다 —
    // 이상 데이터를 "이미 반영됨"으로 덮으면 정합화가 불일치를 숨기게 된다
    private ExternalCancellationResult judgeSameCancellation(Payment payment, Refund refund) {
        return switch (refund.getStatus()) {
            case COMPLETED -> isSettled(payment, refund)
                ? alreadyReconciled(payment, refund)
                : notApplicable(payment, "완료 환불과 결제 상태·요청 주체가 일치하지 않음: refundId=" + refund.getId()
                                         + ", paymentStatus=" + payment.getStatus() + ", requestedBy=" + refund.getRequestedBy());
            // 다음 실행에서 취소 ID로 기존 회원·SYSTEM·마트 거절 환불 경로에 연결된다
            case REQUESTED -> isInProgress(payment, refund)
                ? retry(payment, "같은 취소 ID의 환불이 아직 REQUESTED: refundId=" + refund.getId())
                : notApplicable(payment, inProgressMismatch(payment, refund));
            // 실패로 기록된 환불을 외부 취소 성공으로 조용히 완료하지 않는다
            case FAILED -> notApplicable(payment, "같은 PortOne 취소 ID의 환불이 FAILED 상태: refundId=" + refund.getId());
        };
    }

    // 취소 ID가 아직 없는 진행 중 환불은 외부 취소로 마무리할 대상이다. 다음 실행에서 판정 규칙이 기존 환불에 연결한다
    private ExternalCancellationResult judgeRequested(Payment payment, List<Refund> requested) {
        if (requested.size() > 1) {
            return notApplicable(payment, "REQUESTED 환불이 여러 건: " + requested.size());
        }
        Refund refund = requested.get(0);
        if (refund.getPortoneCancellationId() != null) {
            // 환불 행 하나는 PortOne 취소 하나다. 이미 묶인 취소를 외부 취소로 갈아 끼우지 않는다
            return notApplicable(payment, "기존 REQUESTED 환불에 다른 PortOne 취소 ID가 이미 연결됨: refundId="
                + refund.getId());
        }
        if (!isInProgress(payment, refund)) {
            return notApplicable(payment, inProgressMismatch(payment, refund));
        }
        return retry(payment, "취소 ID 없는 진행 중 환불이 있어 기존 환불로 연결: refundId=" + refund.getId()
            + ", requestedBy=" + refund.getRequestedBy());
    }

    // 완료 환불이 결제 상태와 맞는 정상 조합인지. 회원·외부 취소·마트 거절은 결제를 CANCELED로 닫고, SYSTEM 환불은 FAILED 결제의 돈만 돌려준다
    private static boolean isSettled(Payment payment, Refund refund) {
        return switch (refund.getRequestedBy()) {
            case MEMBER, EXTERNAL, ADMIN -> payment.getStatus() == PaymentStatus.CANCELED;
            case SYSTEM -> payment.getStatus() == PaymentStatus.FAILED;
        };
    }

    // 진행 중 환불이 결제 상태와 맞는 정상 조합인지. 아니면 기다려도 정상화되지 않으므로 재시도하지 않는다
    private static boolean isInProgress(Payment payment, Refund refund) {
        return switch (refund.getRequestedBy()) {
            case MEMBER, ADMIN -> payment.getStatus() == PaymentStatus.CANCEL_REQUESTED;
            case SYSTEM -> payment.getStatus() == PaymentStatus.FAILED;
            case EXTERNAL -> false;
        };
    }

    private static String inProgressMismatch(Payment payment, Refund refund) {
        return "진행 중 환불과 결제 상태가 일치하지 않음: refundId=" + refund.getId()
            + ", requestedBy=" + refund.getRequestedBy() + ", paymentStatus=" + payment.getStatus();
    }

    private ExternalCancellationResult alreadyReconciled(Payment payment, Refund refund) {
        log.info("이미 반영된 외부 취소라 다시 반영하지 않음: paymentId={}, refundId={}", payment.getId(), refund.getId());
        return ExternalCancellationResult.alreadyReconciled();
    }

    private ExternalCancellationResult retry(Payment payment, String reason) {
        log.warn("외부 취소 반영 보류 — 다시 실행: paymentId={}, reason={}", payment.getId(), reason);
        return ExternalCancellationResult.retry(reason);
    }

    // 운영 확인 ERROR는 결과를 기록하는 호출 쪽(웹훅 정합화)이 남긴다
    private ExternalCancellationResult notApplicable(Payment payment, String reason) {
        log.warn("외부 취소 자동 반영 불가: paymentId={}, reason={}", payment.getId(), reason);
        return ExternalCancellationResult.notApplicable(reason);
    }
}
