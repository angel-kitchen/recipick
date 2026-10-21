package com.recipick.domain.product.repository;

import static com.recipick.domain.product.entity.QProduct.product;
import static com.recipick.domain.product.entity.QCategory.category;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private BooleanExpression martIdEq(Long martId){
        return martId == null ? null : product.mart.id.eq(martId);
    }

    private BooleanExpression nameContains(String keyword){
        return StringUtils.hasText(keyword) ? product.name.contains(keyword) : null;
    }

    private BooleanExpression categoryIdEq(Long categoryId){
        return categoryId == null ? null : product.category.id.eq(categoryId);
    }

    private BooleanExpression ingredientIdEq(Long ingredientId){
        return ingredientId == null ? null : product.ingredientId.eq(ingredientId);
    }

    private BooleanExpression statusEq(ProductStatus status){
        return status == null ? null : product.status.eq(status);
    }
}
