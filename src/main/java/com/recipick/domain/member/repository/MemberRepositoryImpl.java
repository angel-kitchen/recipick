package com.recipick.domain.member.repository;

import static com.recipick.domain.member.entity.QMember.member;
import static com.recipick.domain.member.entity.QMembership.membership;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.recipick.domain.member.dto.MemberAdminRow;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<MemberAdminRow> searchMembers(String email, String name, Boolean membershipActive, MembershipType type,
            MembershipSource source, LocalDateTime now, Pageable pageable) {
        BooleanExpression[] conditions = {
                member.withdrawnAt.isNull(),
                StringUtils.hasText(email) ? member.email.containsIgnoreCase(email) : null,
                StringUtils.hasText(name) ? member.name.containsIgnoreCase(name) : null,
                membershipActiveEq(membershipActive, now),
                membershipDetailsEq(type, source, now)
        };
        List<MemberAdminRow> rows = queryFactory.select(Projections.constructor(MemberAdminRow.class,
                        member.id, member.email, member.name, member.martId, member.createdAt))
                .from(member).where(conditions).orderBy(orderBy(pageable))
                .offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();
        JPAQuery<Long> count = queryFactory.select(member.count()).from(member).where(conditions);
        return PageableExecutionUtils.getPage(rows, pageable, count::fetchOne);
    }

    private BooleanExpression membershipDetailsEq(MembershipType type, MembershipSource source, LocalDateTime now) {
        if (type == null && source == null) {
            return null;
        }
        return JPAExpressions.selectOne().from(membership)
                .where(membership.member.id.eq(member.id), membership.startAt.loe(now), membership.endAt.gt(now),
                        membership.terminatedAt.isNull(),
                        type == null ? null : membership.type.eq(type),
                        source == null ? null : membership.source.eq(source)).exists();
    }

    private BooleanExpression membershipActiveEq(Boolean active, LocalDateTime now) {
        if (active == null) {
            return null;
        }
        BooleanExpression exists = JPAExpressions.selectOne().from(membership)
                .where(membership.member.id.eq(member.id), membership.startAt.loe(now), membership.endAt.gt(now),
                        membership.terminatedAt.isNull()).exists();
        return active ? exists : exists.not();
    }

    private OrderSpecifier<?>[] orderBy(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(Sort.Order.desc("createdAt"));
        boolean ascending = order.isAscending();
        OrderSpecifier<?> primary = switch (order.getProperty()) {
            case "id" -> ascending ? member.id.asc() : member.id.desc();
            case "email" -> ascending ? member.email.asc() : member.email.desc();
            case "name" -> ascending ? member.name.asc() : member.name.desc();
            case "createdAt" -> ascending ? member.createdAt.asc() : member.createdAt.desc();
            default -> throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        };
        // 정렬 값이 같은 회원도 페이지 이동 시 순서가 흔들리지 않게 한다.
        return order.getProperty().equals("id") ? new OrderSpecifier<?>[]{primary}
                : new OrderSpecifier<?>[]{primary, member.id.desc()};
    }
}
