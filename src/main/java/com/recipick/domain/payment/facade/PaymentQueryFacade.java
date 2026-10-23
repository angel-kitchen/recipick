package com.recipick.domain.payment.facade;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.dto.response.PaymentDetailResponse;
import com.recipick.domain.payment.dto.response.PaymentHistoryResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

// 결제 조회 api(1-2·1-3)의 진입점. 상태를 바꾸지 않으므로 별도의 Facade 트랜잭션이나 잠금을 걸지 않는다 —
// 각 Service의 readOnly 트랜잭션으로 읽기만 한다. 도메인 Service끼리는 주입하지 않으므로 결제·주문 Service 조합은 여기서 한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentQueryFacade {

    // 최신 결제순으로 조회하되, createdAt이 같은 행의 순서가 페이지마다 흔들리지 않도록 id를 보조 정렬로 둔다
    private static final Sort HISTORY_SORT =
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PaymentService paymentService;
    private final OrderService orderService;

    public PaymentDetailResponse getByOrderId(Long memberId, Long orderId) {
        // 결제가 있으면 주문도 반드시 있다(주문 생성 트랜잭션에서 함께 만들어진다)
        Payment payment = paymentService.getByOrderId(orderId);
        if (!orderService.getMemberIdOf(orderId).equals(memberId)) {
            // 403이면 "그 주문은 있다"를 알려주는 셈이라 404로 답한다
            log.warn("다른 회원의 주문에 결제 조회 시도: memberId={}, orderId={}", memberId, orderId);
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        // 본인 주문으로 확인된 뒤에만 환불 이력을 읽는다
        RefundSummary refundSummary = paymentService.getRefundSummary(payment.getId());
        return PaymentDetailResponse.of(payment, refundSummary);
    }

    public PageResponse<PaymentHistoryResponse> findMyHistory(Long memberId, Pageable pageable) {
        Pageable fixed = fixSort(pageable);
        // PAYMENT에는 회원 정보가 없다. 주문 도메인이 주는 ID 목록으로 내 결제를 좁힌다
        List<Long> orderIds = orderService.findOrderIdsOf(memberId);
        if (orderIds.isEmpty()) {
            return PageResponse.from(Page.empty(fixed));
        }

        Page<Payment> payments = paymentService.findHistory(orderIds, fixed);
        // 결제마다 환불을 조회하면 페이지 크기만큼 쿼리가 늘어난다. 이번 페이지 것만 한 번에 읽는다
        List<Long> paymentIds = payments.getContent().stream().map(Payment::getId).toList();
        Map<Long, RefundSummary> summaries = paymentService.getRefundSummaries(paymentIds);

        return PageResponse.from(payments.map(payment -> PaymentHistoryResponse.of(
            payment, summaries.getOrDefault(payment.getId(), RefundSummary.NONE))));
    }

    // 클라이언트가 보낸 정렬은 버린다. 최신순은 서버가 정하는 계약이라 page·size만 받아 다시 만든다
    private Pageable fixSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), HISTORY_SORT);
    }
}
