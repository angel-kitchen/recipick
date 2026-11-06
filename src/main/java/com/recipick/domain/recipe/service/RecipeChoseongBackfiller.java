package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeChoseongBackfiller implements ApplicationRunner {

    private final RecipeRepository recipeRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        int updated;
        do {
            Integer count = transactionTemplate.execute(status -> {
                List<Recipe> batch =
                    recipeRepository.findTop500ByNameChoseongIsNullAndNameIsNotNullOrderByIdAsc();
                batch.forEach(Recipe::fillNameChoseong);
                return batch.size();
            });
            updated = count == null ? 0 : count;
            total += updated;
        } while (updated > 0);

        if (total > 0) {
            log.info("레시피 초성 backfill 완료: {}건", total);
        }
    }
}
