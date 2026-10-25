package com.recipick.domain.order.repository;

import static com.recipick.domain.order.entity.QOrder.order;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusGroup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 정렬은 요청의 sort와 관계없이 id 내림차순으로 고정한다. (member_id) 인덱스 끝에 PK가 붙어 인덱스 순서로 읽힌다
    @Override
    public Page<Order> searchByMember(Long memberId, OrderStatusGroup statusGroup, Pageable pageable) {
        return pageByIdDesc(pageable, order.memberId.eq(memberId), statusGroupEq(statusGroup));
    }

    // 결제 전(NONE) 주문은 마트가 처리할 대상이 아니라 항상 뺀다. martId가 null이면 전체 마트다.
    // (mart_id, mart_status) 인덱스 끝에 PK가 붙어 인덱스 순서로 읽힌다
    @Override
    public Page<Order> searchByMart(Long martId, MartStatus martStatus, Pageable pageable) {
        return pageByIdDesc(pageable,
                martId == null ? null : order.martId.eq(martId),
                martStatus == null ? null : order.martStatus.eq(martStatus),
                order.martStatus.ne(MartStatus.NONE));
    }

    // 탈퇴 판단용. 종료(CANCELED, REFUNDED, PAID+DELIVERED)가 아니면 모두 진행 중이다.
    // 회원 목록의 ONGOING 탭은 CANCEL_REQUESTED를 취소·환불 탭에 넣으므로 그 조건을 재사용하지 않는다
    @Override
    public boolean existsInProgressByMemberId(Long memberId) {
        return queryFactory
                .selectOne()
                .from(order)
                .where(order.memberId.eq(memberId), closed().not())
                .fetchFirst() != null;
    }

    private Page<Order> pageByIdDesc(Pageable pageable, BooleanExpression... where) {
        List<Order> content = queryFactory
                .selectFrom(order)
                .where(where)
                .orderBy(order.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(order.count())
                .from(order)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression closed() {
        return order.status.in(OrderStatus.CANCELED, OrderStatus.REFUNDED)
                .or(order.status.eq(OrderStatus.PAID).and(order.martStatus.eq(MartStatus.DELIVERED)));
    }

    private BooleanExpression statusGroupEq(OrderStatusGroup statusGroup) {
        if (statusGroup == null) {
            return null;
        }
        return switch (statusGroup) {
            case ONGOING -> order.status.eq(OrderStatus.PENDING_PAYMENT)
                    .or(order.status.eq(OrderStatus.PAID).and(order.martStatus.ne(MartStatus.DELIVERED)));
            case DELIVERED -> order.status.eq(OrderStatus.PAID).and(order.martStatus.eq(MartStatus.DELIVERED));
            case CANCELED_REFUNDED -> order.status.in(
                    OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELED, OrderStatus.REFUNDED);
        };
    }
}
