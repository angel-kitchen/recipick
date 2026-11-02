package com.recipick.domain.order.event;

import com.recipick.domain.order.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 장바구니 정리는 결제 확정과 같은 트랜잭션에 두면 삭제 실패(잠금 대기 초과·교착)가 이미 PG에서 승인된 결제의 확정까지 롤백한다.
// 그래서 결제가 커밋된 뒤에만 지우고, 실패하면 장바구니에 상품이 남는 것으로 끝낸다. 결제 실패·만료·롤백이면 실행되지 않는다.
// 커밋 뒤 콜백에서는 끝난 트랜잭션 자원이 아직 묶여 있어 그대로 참여하면 삭제가 커밋되지 않는다. 그 트랜잭션을 떼어 두고(NOT_SUPPORTED)
// CartService가 새 트랜잭션을 열게 한다. 예외가 커밋 뒤 호출자에게 올라가면 결제 확정 API가 실패로 응답하므로 여기서 끊는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaidCartCleanupListener {

    private final CartService cartService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void removePaidItems(OrderPaidEvent event) {
        try {
            cartService.deductPaidItems(event.memberId(), event.items());
        } catch (DataAccessException e) {
            log.warn("결제 완료 주문의 장바구니 정리 실패 — 장바구니에 상품이 남음: orderId={}, memberId={}",
                event.orderId(), event.memberId(), e);
        } catch (RuntimeException e) {
            log.error("결제 완료 주문의 장바구니 정리 중 예상하지 못한 오류: orderId={}, memberId={}",
                event.orderId(), event.memberId(), e);
        }
    }
}
