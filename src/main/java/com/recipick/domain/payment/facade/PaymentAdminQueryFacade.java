package com.recipick.domain.payment.facade;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.dto.request.PaymentAdminSearchRequest;
import com.recipick.domain.payment.dto.response.PaymentAdminDetailResponse;
import com.recipick.domain.payment.dto.response.PaymentAdminSummaryResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.repository.dto.PaymentAdminSearchCondition;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 관리자 결제 조회의 진입점. 회원용 PaymentQueryFacade와 분리해 소유권 검사가 섞이지 않게 한다.
// 관리자 조회는 감사 기록 대상이고 AdminAuditRecorder는 쓰기 가능한 트랜잭션을 요구하므로 readOnly를 쓰지 않는다.
// 조회와 감사 기록이 한 트랜잭션이라, 기록이 실패하면 롤백되고 조회 결과도 응답하지 않는다
@Component
@RequiredArgsConstructor
public class PaymentAdminQueryFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final AdminAuditRecorder adminAuditRecorder;

    @Transactional
    public PageResponse<PaymentAdminSummaryResponse> findPayments(PaymentAdminSearchRequest request) {
        PageResponse<PaymentAdminSummaryResponse> response = search(request, request.toPageable());
        // 결과가 0건이어도 실제 관리자 조회이므로 기록한다. 검색 조건은 감사 로그에 복사하지 않는다
        adminAuditRecorder.record(
            new AdminAuditCommand(AdminAuditAction.PAYMENT_LIST_VIEW, null, null, null));
        return response;
    }

    @Transactional
    public PaymentAdminDetailResponse getPayment(Long paymentId) {
        // 결제가 없으면 여기서 PAYMENT-001로 끝나고 감사 기록도 남지 않는다
        Payment payment = paymentService.getById(paymentId);
        // 결제가 있으면 주문도 반드시 있다(주문 생성 트랜잭션에서 함께 만들어진다)
        Long memberId = orderService.getMemberIdOf(payment.getOrderId());
        List<Refund> refunds = paymentService.findRefundsOf(paymentId);
        PaymentAdminDetailResponse response = PaymentAdminDetailResponse.of(payment, memberId, refunds);

        adminAuditRecorder.record(
            new AdminAuditCommand(AdminAuditAction.PAYMENT_DETAIL_VIEW, paymentId, null, null));
        return response;
    }

    private PageResponse<PaymentAdminSummaryResponse> search(PaymentAdminSearchRequest request, Pageable pageable) {
        // PAYMENT에는 회원 정보가 없다. 회원 조건은 주문 도메인이 주는 주문 ID 목록으로 바꿔 건다
        List<Long> orderIds = null;
        if (request.memberId() != null) {
            orderIds = orderService.findOrderIdsOf(request.memberId());
            if (orderIds.isEmpty()) {
                return PageResponse.from(Page.empty(pageable));
            }
        }

        PaymentAdminSearchCondition condition = new PaymentAdminSearchCondition(
            request.toPaymentStatus(), orderIds, request.toFromAt(), request.toToExclusive());
        Page<Payment> payments = paymentService.searchForAdmin(condition, pageable);

        // 결제마다 환불·회원을 조회하면 페이지 크기만큼 쿼리가 늘어난다. 이번 페이지 것만 한 번씩 묶어 읽는다
        List<Payment> content = payments.getContent();
        Map<Long, RefundSummary> summaries = paymentService.getRefundSummaries(
            content.stream().map(Payment::getId).toList());
        Map<Long, Long> memberIds = orderService.findMemberIdsOf(
            content.stream().map(Payment::getOrderId).toList());

        return PageResponse.from(payments.map(payment -> PaymentAdminSummaryResponse.of(
            payment,
            memberIds.get(payment.getOrderId()),
            summaries.getOrDefault(payment.getId(), RefundSummary.NONE))));
    }
}
