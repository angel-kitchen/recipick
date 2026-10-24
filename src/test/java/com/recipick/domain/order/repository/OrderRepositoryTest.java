package com.recipick.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusGroup;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderRepositoryTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long MART_ID = 910L;
    private static final Long OTHER_MART_ID = 911L;

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

    @ParameterizedTest
    @EnumSource(OrderStatusGroup.class)
    @NullSource
    @DisplayName("회원 주문을 탭 묶음 조건으로 거르고 최신 주문부터 돌려주며 다른 회원 주문은 빼고 돌려준다")
    void searchByMember(OrderStatusGroup statusGroup) {
        // given
        Order pending = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PENDING_PAYMENT, MartStatus.NONE));
        Order received = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        Order shipping = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PAID, MartStatus.SHIPPING));
        Order delivered = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PAID, MartStatus.DELIVERED));
        Order canceled = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.CANCELED, MartStatus.NONE));
        Order rejected = orderRepository.save(
                newOrder(MEMBER_ID, OrderStatus.CANCEL_REQUESTED, MartStatus.REJECTED));
        Order refunded = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.REFUNDED, MartStatus.RECEIVED));
        orderRepository.save(newOrder(OTHER_MEMBER_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        em.flush();
        em.clear();
        Map<OrderStatusGroup, List<Order>> expectedByGroup = new HashMap<>();
        expectedByGroup.put(null, List.of(refunded, rejected, canceled, delivered, shipping, received, pending));
        expectedByGroup.put(OrderStatusGroup.ONGOING, List.of(shipping, received, pending));
        expectedByGroup.put(OrderStatusGroup.DELIVERED, List.of(delivered));
        expectedByGroup.put(OrderStatusGroup.CANCELED_REFUNDED, List.of(refunded, rejected, canceled));

        // when
        Page<Order> page = orderRepository.searchByMember(MEMBER_ID, statusGroup, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent())
                .extracting(Order::getId)
                .containsExactlyElementsOf(expectedByGroup.get(statusGroup).stream().map(Order::getId).toList());
        assertThat(page.getTotalElements()).isEqualTo(expectedByGroup.get(statusGroup).size());
    }

    @Test
    @DisplayName("회원 주문을 페이지 크기만큼 자르고 요청한 정렬과 관계없이 id 내림차순으로 돌려준다")
    void searchByMember_paging() {
        // given
        Order first = orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        orderRepository.save(newOrder(MEMBER_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        em.flush();
        em.clear();

        // when
        Page<Order> page = orderRepository.searchByMember(
                MEMBER_ID, null, PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "id")));

        // then
        assertThat(page.getContent()).extracting(Order::getId).containsExactly(first.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("마트 주문을 처리 상태로 거르고 최신 주문부터 돌려주며 결제 전 주문과 다른 마트 주문은 빼고 돌려준다")
    void searchByMart() {
        // given
        Order received = orderRepository.save(newMartOrder(MART_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        Order preparing = orderRepository.save(newMartOrder(MART_ID, OrderStatus.PAID, MartStatus.PREPARING));
        orderRepository.save(newMartOrder(MART_ID, OrderStatus.PENDING_PAYMENT, MartStatus.NONE));
        orderRepository.save(newMartOrder(OTHER_MART_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        em.flush();
        em.clear();

        // when
        Page<Order> all = orderRepository.searchByMart(MART_ID, null, PageRequest.of(0, 20));
        Page<Order> preparingOnly = orderRepository.searchByMart(MART_ID, MartStatus.PREPARING, PageRequest.of(0, 20));

        // then
        assertThat(all.getContent()).extracting(Order::getId).containsExactly(preparing.getId(), received.getId());
        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(preparingOnly.getContent()).extracting(Order::getId).containsExactly(preparing.getId());
        assertThat(preparingOnly.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("마트 ID 없이 조회하면 전체 마트의 결제 후 주문을 최신 주문부터 돌려준다")
    void searchByMart_allMarts() {
        // given
        Order mine = orderRepository.save(newMartOrder(MART_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        Order others = orderRepository.save(newMartOrder(OTHER_MART_ID, OrderStatus.PAID, MartStatus.RECEIVED));
        Order pending = orderRepository.save(newMartOrder(MART_ID, OrderStatus.PENDING_PAYMENT, MartStatus.NONE));
        em.flush();
        em.clear();

        // when
        Page<Order> page = orderRepository.searchByMart(null, null, PageRequest.of(0, 100));

        // then
        assertThat(page.getContent()).extracting(Order::getId)
                .containsSubsequence(others.getId(), mine.getId())
                .doesNotContain(pending.getId());
    }

    private Order newMartOrder(Long martId, OrderStatus status, MartStatus martStatus) {
        return Order.builder()
                .memberId(MEMBER_ID)
                .martId(martId)
                .status(status)
                .martStatus(martStatus)
                .build();
    }

    private Order newOrder(Long memberId, OrderStatus status, MartStatus martStatus) {
        return Order.builder()
                .memberId(memberId)
                .status(status)
                .martStatus(martStatus)
                .build();
    }

    private Order newOrder(Long memberId, OrderStatus status) {
        return Order.builder()
                .memberId(memberId)
                .status(status)
                .martStatus(MartStatus.NONE)
                .build();
    }
}
