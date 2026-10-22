package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.recipe.dto.request.RecipeAutoCartUpdateRequest;
import com.recipick.domain.recipe.dto.request.RecipeIngredientsSaveRequest;
import com.recipick.domain.recipe.dto.response.RecipeAdminDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeIngredientsSaveResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RecipeAdminFacadeIntegrationTest extends IntegrationTestSupport {

    @Autowired
    RecipeAdminFacade recipeAdminFacade;
    @Autowired
    RecipeRepository recipeRepository;
    @Autowired
    IngredientRepository ingredientRepository;
    @Autowired
    RecipeIngredientRepository recipeIngredientRepository;
    @Autowired
    MartRepository martRepository;
    @Autowired
    ProductRepository productRepository;

    private Recipe recipe;
    private Ingredient tofu;
    private Ingredient zucchini;
    private Mart mart;
    private Product tofuProduct;

    @BeforeEach
    void setUp() {
        recipe = recipeRepository.save(Recipe.builder()
            .name("통합 테스트 된장찌개")
            .baseServings(2)
            .steps(List.of())
            .build());
        tofu = ingredientRepository.save(Ingredient.builder().name("통합테스트두부").unit(SaleUnit.G).build());
        zucchini = ingredientRepository.save(Ingredient.builder().name("통합테스트애호박").unit(SaleUnit.EA).build());
        mart = martRepository.save(Mart.builder()
            .regionId(9301L)
            .name("레시피 통합 테스트 마트")
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build());
        tofuProduct = productRepository.save(Product.builder()
            .mart(mart)
            .ingredientId(tofu.getId())
            .name("국산콩 두부 300g")
            .totalQuantity(10)
            .deleted(false)
            .build());
    }

    @AfterEach
    void cleanUp() {
        recipeIngredientRepository.deleteAll(recipeIngredientRepository.findAllWithIngredient(recipe.getId()));
        recipeRepository.deleteById(recipe.getId());
        productRepository.deleteById(tofuProduct.getId());
        martRepository.deleteById(mart.getId());
        ingredientRepository.deleteAllById(List.of(tofu.getId(), zucchini.getId()));
    }

    private RecipeIngredientsSaveRequest request(RecipeIngredientsSaveRequest.Item... items) {
        return new RecipeIngredientsSaveRequest(List.of(items));
    }

    private RecipeIngredientsSaveRequest.Item item(Ingredient ingredient, String amount, boolean required) {
        return new RecipeIngredientsSaveRequest.Item(ingredient.getId(), new BigDecimal(amount), required);
    }

    @Test
    @DisplayName("같은 재료를 다시 저장해도 UNIQUE 위반 없이 새 값으로 바뀐다")
    void saveIngredients_sameIngredientAgain() {
        // given
        recipeAdminFacade.saveIngredients(recipe.getId(), request(item(tofu, "100", true)));

        // when
        recipeAdminFacade.saveIngredients(recipe.getId(),
            request(item(tofu, "150.5", true), item(zucchini, "0.5", false)));

        // then
        RecipeAdminDetailResponse detail = recipeAdminFacade.getRecipe(recipe.getId());
        assertThat(detail.ingredients())
            .extracting(RecipeAdminDetailResponse.IngredientResponse::amountPerServing)
            .extracting(BigDecimal::toPlainString)
            .containsExactly("150.5", "0.5");
        assertThat(detail.ingredients())
            .extracting(RecipeAdminDetailResponse.IngredientResponse::linkedProductCount)
            .containsExactly(1L, 0L);
    }

    @Test
    @DisplayName("지정 뒤 연결 상품 없는 필수 재료가 추가되면 자동 장보기 지정이 자동으로 풀린다")
    void saveIngredients_releasesAutoCart() {
        // given
        recipeAdminFacade.saveIngredients(recipe.getId(), request(item(tofu, "100", true)));
        recipeAdminFacade.changeAutoCart(recipe.getId(), new RecipeAutoCartUpdateRequest(true));

        // when
        RecipeIngredientsSaveResponse response = recipeAdminFacade.saveIngredients(recipe.getId(),
            request(item(tofu, "100", true), item(zucchini, "1", true)));

        // then
        assertThat(response.autoCartReleased()).isTrue();
        assertThat(recipeRepository.findById(recipe.getId()).orElseThrow().isAutoCartEnabled()).isFalse();
    }
}
