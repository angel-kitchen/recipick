package com.recipick.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderItemRepositoryTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 1L;
    // 시연 마트·다른 테스트와 겹치지 않는 ID를 쓴다. mart_id, product_id에 FK가 없어 저장된다
    private static final Long MART_ID = 920L;
    private static final Long OTHER_MART_ID = 921L;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("베스트: 결제 완료 주문의 수량 합계가 큰 상품부터 돌려주고 대상이 아닌 주문은 뺀다")
    void findBestSellingProductIds() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Order first = paidOrder(MART_ID, now);
        item(first, 1L, 2);
        item(first, 2L, 1);
        Order second = paidOrder(MART_ID, now);
        item(second, 2L, 3);
        Order rejected = paidOrder(MART_ID, now);
        item(rejected, 3L, 100);
        Order refunded = paidOrder(MART_ID, now);
        item(refunded, 4L, 100);
        Order pending = orderRepository.save(newOrder(MART_ID, OrderStatus.PENDING_PAYMENT, MartStatus.NONE));
        item(pending, 5L, 100);
        Order old = paidOrder(MART_ID, now.minusDays(10));
        item(old, 6L, 100);
        Order otherMart = paidOrder(OTHER_MART_ID, now);
        item(otherMart, 7L, 100);
        em.flush();
        // 영속성 컨텍스트를 거치지 않는 벌크 UPDATE로 마트 거절(취소 요청)·환불 상태를 만든다
        em.createQuery("update Order o set o.status = :status where o.id = :id")
            .setParameter("status", OrderStatus.CANCEL_REQUESTED)
            .setParameter("id", rejected.getId())
            .executeUpdate();
        em.createQuery("update Order o set o.status = :status where o.id = :id")
            .setParameter("status", OrderStatus.REFUNDED)
            .setParameter("id", refunded.getId())
            .executeUpdate();
        em.clear();

        // when
        List<Long> weekly = findBest(MART_ID, now.minusDays(7), 20);
        List<Long> all = findBest(MART_ID, LocalDateTime.of(2000, 1, 1, 0, 0), 20);

        // then
        assertThat(weekly).containsExactly(2L, 1L);
        assertThat(all).containsExactly(6L, 2L, 1L);
    }

    @Test
    @DisplayName("베스트: 수량이 같으면 상품 ID가 큰 상품이 먼저이고 상위 N개까지만 돌려준다")
    void findBestSellingProductIds_tieAndLimit() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Order order = paidOrder(MART_ID, now);
        item(order, 11L, 5);
        item(order, 12L, 5);
        item(order, 13L, 1);
        em.flush();
        em.clear();

        // when
        List<Long> top2 = findBest(MART_ID, now.minusDays(1), 2);

        // then
        assertThat(top2).containsExactly(12L, 11L);
    }

    @Test
    @DisplayName("베스트 집계용 인덱스가 마트 ID, 상태, 결제 시각 순서로 생성된다")
    void bestSellerIndexExists() {
        // when
        List<?> rows = em.createNativeQuery(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' "
                    + "AND INDEX_NAME = 'idx_orders_mart_id_status_paid_at' ORDER BY SEQ_IN_INDEX")
            .getResultList();

        // then
        assertThat(rows.stream().map(Object::toString).toList()).containsExactly("mart_id", "status", "paid_at");
    }

    private List<Long> findBest(Long martId, LocalDateTime from, int limit) {
        return orderItemRepository.findBestSellingProductIds(
            martId, OrderStatus.PAID, from, PageRequest.of(0, limit));
    }

    private Order paidOrder(Long martId, LocalDateTime paidAt) {
        Order order = newOrder(martId, OrderStatus.PENDING_PAYMENT, MartStatus.NONE);
        order.markPaid(paidAt);
        return orderRepository.save(order);
    }

    private Order newOrder(Long martId, OrderStatus status, MartStatus martStatus) {
        return Order.builder()
            .memberId(MEMBER_ID)
            .martId(martId)
            .status(status)
            .martStatus(martStatus)
            .build();
    }

    private void item(Order order, Long productId, int quantity) {
        orderItemRepository.save(OrderItem.builder()
            .order(order)
            .productId(productId)
            .quantity(quantity)
            .build());
    }
}
