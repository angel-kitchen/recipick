package com.recipick.domain.product.repository;

import static com.recipick.domain.product.entity.QProduct.product;
import static com.recipick.domain.product.entity.QCategory.category;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.recipick.domain.product.repository.dto.MemberProductSearchCondition;
import com.recipick.global.common.Choseong;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;



@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> searchAdminProducts(AdminProductSearchCondition condition, Pageable pageable) {
        BooleanExpression[] where = {
            product.deleted.isFalse(),
            martIdEq(condition.martId()),
            nameContains(condition.keyword()),
            categoryIdEq(condition.categoryId()),
            ingredientIdEq(condition.ingredientId()),
            statusEq(condition.status())
        };

        List<Product> content = queryFactory
            .selectFrom(product)
            .leftJoin(product.category, category).fetchJoin()
            .where(where)
            .orderBy(product.createdAt.desc(), product.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        JPAQuery<Long> countQuery = queryFactory
            .select(product.count())
            .from(product)
            .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression martIdEq(Long martId) {
        return martId == null ? null : product.mart.id.eq(martId);
    }

    // 자동완성과 같은 기준으로 이름과 검색어의 띄어쓰기를 모두 지우고 비교한다
    private StringExpression withoutSpaces(StringExpression source) {
        return Expressions.stringTemplate("replace({0}, ' ', '')", source);
    }

    private BooleanExpression nameContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String query = keyword.replace(" ", "");
        StringExpression source = Choseong.isChoseongOnly(query) ? product.nameChoseong : product.name;
        return withoutSpaces(source).contains(query);
    }

    private BooleanExpression categoryIdEq(Long categoryId) {
        return categoryId == null ? null : product.category.id.eq(categoryId);
    }

    private BooleanExpression ingredientIdEq(Long ingredientId) {
        return ingredientId == null ? null : product.ingredientId.eq(ingredientId);
    }

    private BooleanExpression statusEq(ProductStatus status) {
        return status == null ? null : product.status.eq(status);
    }

    @Override
    public List<Product> findSuggestions(Long martId, String query, int limit) {
        StringExpression source = Choseong.isChoseongOnly(query) ? product.nameChoseong : product.name;
        StringExpression target = withoutSpaces(source).lower();
        NumberExpression<Integer> prefixRank = new CaseBuilder()
            .when(target.startsWith(query)).then(0)
            .otherwise(1);

        return queryFactory
            .selectFrom(product)
            .where(product.deleted.isFalse(), martIdEq(martId), target.contains(query))
            .orderBy(prefixRank.asc(), product.name.asc(), product.id.asc())
            .limit(limit)
            .fetch();
    }

    @Override
    public Page<Product> searchMemberProducts(MemberProductSearchCondition condition, Pageable pageable) {
        BooleanExpression[] where = {
            product.deleted.isFalse(),
            martIdEq(condition.martId()),
            nameContains(condition.keyword()),
            categoryIdEq(condition.categoryId()),
            priceGoe(condition.minPrice(), condition.now()),
            priceLoe(condition.maxPrice(), condition.now()),
            onSaleOnly(condition.onSale(), condition.now())
        };

        List<Product> content = queryFactory
            .selectFrom(product)
            .where(where)
            .orderBy(memberOrderSpecifiers(pageable.getSort(), condition.now()))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        JPAQuery<Long> countQuery = queryFactory
            .select(product.count())
            .from(product)
            .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private OrderSpecifier<?>[] memberOrderSpecifiers(Sort sort, LocalDateTime now) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            switch (order.getProperty()) {
                case "price" -> orders.add(new OrderSpecifier<>(direction, salePrice(now)));
                case "createdAt" -> orders.add(new OrderSpecifier<>(direction, product.createdAt));
                case "discountEndAt" -> orders.add(new OrderSpecifier<>(direction, product.discountEndAt));
                default -> throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다 : " +
                    order.getProperty());
            }
        }
        if (orders.isEmpty()) {
            orders.add(product.createdAt.desc());
        }
        orders.add(product.id.desc());
        return orders.toArray(OrderSpecifier[]::new);
    }

    private BooleanExpression onSaleOnly(boolean onSale, LocalDateTime now) {
        return onSale ? product.discountRate.gt(0).and(product.discountEndAt.after(now)) : null;
    }

    private BooleanExpression priceGoe(Long minPrice, LocalDateTime now) {
        return minPrice == null ? null : salePrice(now).goe(minPrice);
    }

    private BooleanExpression priceLoe(Long maxPrice, LocalDateTime now) {
        return maxPrice == null ? null : salePrice(now).loe(maxPrice);
    }

    // 할인 중이면 정상가에서 할인율을 뺀 값(원 단위 내림), 아니면 정상가. 정렬·가격 범위를 판매가 기준으로 맞춘다
    private NumberExpression<Long> salePrice(LocalDateTime now) {
        return new CaseBuilder()
            .when(product.discountRate.gt(0).and(product.discountEndAt.after(now)))
            .then(Expressions.numberTemplate(
                Long.class, "floor({0} * (100 - {1}) / 100.0)", product.price, product.discountRate))
            .otherwise(product.price);
    }
}
