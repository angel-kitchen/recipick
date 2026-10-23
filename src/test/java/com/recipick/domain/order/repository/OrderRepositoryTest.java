package com.recipick.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderRepositoryTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("회원의 주문 ID를 상태와 관계없이 최신순으로 돌려주고 다른 회원 주문은 빼고 돌려준다")
    void findIdsByMemberId() {
        // given
        Order canceled = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.CANCELED));
        Order paid = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PAID));
        orderRepository.save(newOrder(OTHER_MEMBER_ID, OrderStatus.PAID));
        em.flush();
        em.clear();

        // when
        List<Long> ids = orderRepository.findIdsByMemberId(MEMBER_ID);

        // then
        assertThat(ids).containsExactly(paid.getId(), canceled.getId());
    }

    @Test
    @DisplayName("주문 ID 목록으로 각 주문의 회원 ID를 돌려주고 없는 주문 ID는 빼고 돌려준다")
    void findMemberIdsByIdIn() {
        // given
        Order mine = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PAID));
        Order others = orderRepository.save(newOrder(OTHER_MEMBER_ID, OrderStatus.PAID));
        em.flush();
        em.clear();

        // when
        List<OrderMemberId> rows = orderRepository.findMemberIdsByIdIn(
                List.of(mine.getId(), others.getId(), Long.MAX_VALUE));

        // then
        assertThat(rows)
                .extracting(OrderMemberId::getOrderId, OrderMemberId::getMemberId)
                .containsExactlyInAnyOrder(
                        tuple(mine.getId(), MEMBER_ID),
                        tuple(others.getId(), OTHER_MEMBER_ID));
    }

    @Test
    @DisplayName("주문 목록 조회용 인덱스가 회원 ID, 마트 ID·마트 처리 상태 순서로 생성된다")
    void listIndexesExist() {
        // when
        List<?> rows = em.createNativeQuery(
                        "SELECT INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' "
                                + "AND INDEX_NAME IN ('idx_orders_member_id', 'idx_orders_mart_id_mart_status') "
                                + "ORDER BY INDEX_NAME, SEQ_IN_INDEX")
                .getResultList();

        // then
        assertThat(rows)
                .extracting(row -> tuple(((Object[]) row)[0], ((Object[]) row)[1]))
                .containsExactly(
                        tuple("idx_orders_mart_id_mart_status", "mart_id"),
                        tuple("idx_orders_mart_id_mart_status", "mart_status"),
                        tuple("idx_orders_member_id", "member_id"));
    }

    private Order newOrder(Long memberId, OrderStatus status) {
        return Order.builder()
                .memberId(memberId)
                .status(status)
                .martStatus(MartStatus.NONE)
                .build();
    }
}
