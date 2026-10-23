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
        BooleanExpression[] where = {order.memberId.eq(memberId), statusGroupEq(statusGroup)};

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
