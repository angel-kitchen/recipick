package com.recipick.domain.recipe.repository;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.DishType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.global.common.Choseong;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.recipick.domain.recipe.entity.QRecipe.recipe;

@RequiredArgsConstructor
public class RecipeRepositoryImpl implements RecipeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Recipe> search(RecipeSearchCondition condition, Pageable pageable) {
        Predicate[] where = {
                nameContains(condition.keyword()),
                cuisineEq(condition.cuisineType()),
                dishTypeEq(condition.dishType()),
                autoCartEnabledEq(condition.autoCartAvailable()),
                statusEq(condition.status())
        };

        List<Recipe> content = queryFactory
            .selectFrom(recipe)
            .where(where)
            .orderBy(recipe.name.asc(), recipe.id.asc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        JPAQuery<Long> countQuery = queryFactory
            .select(recipe.count())
            .from(recipe)
            .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression nameContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String query = keyword.replace(" ", "");
        StringExpression source = Choseong.isChoseongOnly(query) ? recipe.nameChoseong : recipe.name;
        return Expressions.stringTemplate("replace({0}, ' ', '')", source).contains(query);
    }

    private BooleanExpression cuisineEq(CuisineType cuisineType) {
        return cuisineType == null ? null : recipe.cuisine.eq(cuisineType);
    }

    private BooleanExpression dishTypeEq(DishType dishType) {
        return dishType == null ? null : recipe.dishType.eq(dishType);
    }

    private BooleanExpression autoCartEnabledEq(Boolean autoCartAvailable) {
        return autoCartAvailable == null ? null : recipe.autoCartEnabled.eq(autoCartAvailable);
    }

    private BooleanExpression statusEq(RecipeStatus status) {
        return status == null ? null : recipe.status.eq(status);
    }
}
