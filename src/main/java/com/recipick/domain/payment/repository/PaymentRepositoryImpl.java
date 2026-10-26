package com.recipick.domain.payment.repository;

import static com.recipick.domain.payment.entity.QPayment.payment;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.repository.dto.PaymentAdminSearchCondition;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // null인 조건은 where에서 빠진다. 기간은 createdAt 기준 [fromAt, toExclusive)이다
    @Override
    public Page<Payment> searchForAdmin(PaymentAdminSearchCondition condition, Pageable pageable) {
        BooleanExpression[] conditions = {
            condition.status() == null ? null : payment.status.eq(condition.status()),
            condition.orderIds() == null ? null : payment.orderId.in(condition.orderIds()),
            condition.fromAt() == null ? null : payment.createdAt.goe(condition.fromAt()),
            condition.toExclusive() == null ? null : payment.createdAt.lt(condition.toExclusive())
        };

        List<Payment> content = queryFactory.selectFrom(payment)
            .where(conditions)
            .orderBy(orderBy(pageable))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        // 마지막 페이지처럼 count가 필요 없는 경우 count 쿼리를 생략한다
        JPAQuery<Long> count = queryFactory.select(payment.count())
            .from(payment)
            .where(conditions);
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    // 1차 계약은 createdAt 정렬만 연다. 보조 정렬 id는 주 정렬과 같은 방향이다
    private OrderSpecifier<?>[] orderBy(Pageable pageable) {
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        if (order == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return order.isAscending()
            ? new OrderSpecifier<?>[]{payment.createdAt.asc(), payment.id.asc()}
            : new OrderSpecifier<?>[]{payment.createdAt.desc(), payment.id.desc()};
    }
}
