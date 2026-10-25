package com.recipick.domain.recipe.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.response.MemberMeResponse.MembershipSummary;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.dto.response.AutoCartPreviewResponse;
import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import com.recipick.domain.recipe.entity.AutoCartStatus;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.UnavailableReason;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.AutoCartRequestRepository;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

class AutoCartFacadeIntegrationTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9401L;

    @Autowired
    AutoCartFacade autoCartFacade;
    @Autowired
    AutoCartRequestRepository autoCartRequestRepository;
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

    // 회원·멤버십은 다른 도메인의 가입·결제 흐름을 거쳐야 만들어져, 자동 구성 흐름만 보도록 결과를 고정한다
    @MockitoBean
    MemberService memberService;
    @MockitoBean
    MembershipService membershipService;

    private Recipe recipe;
    private Ingredient tofu;
    private Ingredient pepper;
    private Mart mart;
    private Product tofuProduct;

    @BeforeEach
    void setUp() {
        tofu = ingredientRepository.save(Ingredient.builder().name("자동구성두부").unit(SaleUnit.G).build());
        pepper = ingredientRepository.save(Ingredient.builder().name("자동구성청양고추").unit(SaleUnit.EA).build());
        recipe = recipeRepository.save(Recipe.builder()
            .name("자동구성 된장찌개")
            .baseServings(2)
            .steps(List.of())
            .build());
        recipeIngredientRepository.saveAll(List.of(
            RecipeIngredient.builder().recipe(recipe).ingredient(tofu)
                .amountPerServing(new BigDecimal("100")).required(true).build(),
            RecipeIngredient.builder().recipe(recipe).ingredient(pepper)
                .amountPerServing(new BigDecimal("0.5")).required(false).build()
        ));
        recipe.enableAutoCart();
        recipeRepository.save(recipe);
        mart = martRepository.save(Mart.builder()
            .regionId(9401L)
            .name("자동구성 테스트 마트")
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build());
        tofuProduct = productRepository.save(Product.builder()
            .mart(mart)
            .ingredientId(tofu.getId())
            .name("국산콩 두부 300g")
            .saleUnitAmount(new BigDecimal("300"))
            .saleUnit(SaleUnit.G)
            .price(2500L)
            .totalQuantity(10)
            .deleted(false)
            .build());

        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(mart.getId()));
        given(membershipService.getSummary(eq(MEMBER_ID), any()))
            .willReturn(new MembershipSummary(true, "TRIAL", null));
    }

    @AfterEach
    void cleanUp() {
        autoCartRequestRepository.deleteAll();
        productRepository.deleteById(tofuProduct.getId());
        martRepository.deleteById(mart.getId());
        recipeIngredientRepository.deleteAll(recipeIngredientRepository.findAllWithIngredient(recipe.getId()));
        recipeRepository.deleteById(recipe.getId());
        ingredientRepository.deleteAllById(List.of(tofu.getId(), pepper.getId()));
    }

    private AutoCartPreviewRequest previewRequest(int servings, List<Long> optionalIngredientIds) {
        return new AutoCartPreviewRequest(
            List.of(new AutoCartPreviewRequest.Menu(recipe.getId(), servings, optionalIngredientIds)));
    }

    @Test
    @DisplayName("미리보기: 이용 마트 상품으로 계산하고 PREVIEWED 요청과 메뉴를 저장한다")
    void preview() {
        // when
        AutoCartPreviewResponse response =
            autoCartFacade.preview(MEMBER_ID, previewRequest(5, List.of(pepper.getId())));

        // then
        assertThat(response.status()).isEqualTo(AutoCartStatus.PREVIEWED);
        assertThat(response.martName()).isEqualTo("자동구성 테스트 마트");
        assertThat(response.items())
            .extracting(AutoCartPreviewResponse.Item::ingredientId, AutoCartPreviewResponse.Item::purchaseQuantity,
                AutoCartPreviewResponse.Item::linePrice, AutoCartPreviewResponse.Item::unavailableReason)
            .containsExactly(
                tuple(tofu.getId(), 2, 5000L, null),
                tuple(pepper.getId(), 0, 0L, UnavailableReason.NOT_SOLD_IN_MART));
        assertThat(response.items().get(0).expectedRemainingAmount().toPlainString()).isEqualTo("100");
        assertThat(response.totalPrice()).isEqualTo(5000L);

        AutoCartRequest saved = autoCartRequestRepository.findById(response.autoCartRequestId()).orElseThrow();
        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getMartId()).isEqualTo(mart.getId());
        assertThat(saved.getTotalPrice()).isEqualTo(5000L);
        assertThat(saved.getMenus())
            .containsExactly(new AutoCartMenu(recipe.getId(), "자동구성 된장찌개", 5, List.of(pepper.getId())));
    }

    @Test
    @DisplayName("다시 조회: 확정 전 요청은 저장된 메뉴로 현재 가격 기준 다시 계산한다")
    void getAutoCart_recalculatesPreviewed() {
        // given
        Long autoCartRequestId = autoCartFacade.preview(MEMBER_ID, previewRequest(5, List.of())).autoCartRequestId();
        ReflectionTestUtils.setField(tofuProduct, "price", 3000L);
        productRepository.save(tofuProduct);

        // when
        AutoCartPreviewResponse response = autoCartFacade.getAutoCart(MEMBER_ID, autoCartRequestId);

        // then
        assertThat(response.totalPrice()).isEqualTo(6000L);
    }

    @Test
    @DisplayName("다시 조회: 다른 회원의 요청이면 AUTO_CART_REQUEST_NOT_FOUND 예외를 던진다")
    void getAutoCart_otherMember() {
        // given
        Long autoCartRequestId = autoCartFacade.preview(MEMBER_ID, previewRequest(2, List.of())).autoCartRequestId();

        // when
        Throwable thrown = catchThrowable(() -> autoCartFacade.getAutoCart(MEMBER_ID + 1, autoCartRequestId));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.AUTO_CART_REQUEST_NOT_FOUND));
    }
}
