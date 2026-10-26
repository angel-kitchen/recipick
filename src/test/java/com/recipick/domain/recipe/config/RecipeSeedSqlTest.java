package com.recipick.domain.recipe.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.recipe.entity.CuisineType;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.repository.RecipeRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

// 레시피 초기 데이터는 로컬에서만 실행돼 다른 테스트로는 SQL 오류를 잡을 수 없으므로, 같은 파일을 테스트 DB에 직접 실행해 본다
class RecipeSeedSqlTest extends IntegrationTestSupport {

    private static final String SEED_SQL = "data-recipe-local.sql";

    @Autowired
    DataSource dataSource;
    @Autowired
    RecipeRepository recipeRepository;

    private Set<Long> existingIds;

    @BeforeEach
    void setUp() {
        existingIds = recipeRepository.findAll().stream()
            .map(Recipe::getId)
            .collect(Collectors.toSet());
    }

    @AfterEach
    void cleanUp() {
        recipeRepository.deleteAll(addedRecipes());
    }

    private List<Recipe> addedRecipes() {
        return recipeRepository.findAll().stream()
            .filter(recipe -> !existingIds.contains(recipe.getId()))
            .toList();
    }

    private void runSeedSql() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SEED_SQL));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
    }

    @Test
    @DisplayName("레시피 초기 데이터 SQL을 두 번 실행해도 한식 70개·양식 30개만 조리 순서와 영상을 갖고 들어간다")
    void seedSql_loadsOnceWithStepsAndVideo() {
        // when
        runSeedSql();
        runSeedSql();

        // then
        List<Recipe> added = addedRecipes();
        assertThat(added).hasSize(100);
        assertThat(added).filteredOn(recipe -> recipe.getCuisine() == CuisineType.KOREAN).hasSize(70);
        assertThat(added).filteredOn(recipe -> recipe.getCuisine() == CuisineType.WESTERN).hasSize(30);
        assertThat(added).allSatisfy(recipe -> {
            assertThat(recipe.getSteps()).isNotEmpty();
            assertThat(recipe.getYoutubeVideoId()).hasSize(11);
            assertThat(recipe.isActive()).isTrue();
        });
    }
}
