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
import com.recipick.domain.order.entity.CartAddHistory;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.enums.CartAddSource;
import com.recipick.domain.order.repository.CartAddHistoryRepository;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.recipe.dto.request.AutoCartConfirmRequest;
import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.dto.response.AutoCartConfirmResponse;
import com.recipick.domain.recipe.dto.response.AutoCartPreviewResponse;
import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import com.recipick.domain.recipe.entity.AutoCartStatus;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.UnavailableReason;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.AutoCartItemRepository;
import com.recipick.domain.recipe.repository.AutoCartRequestRepository;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    AutoCartItemRepository autoCartItemRepository;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    CartAddHistoryRepository cartAddHistoryRepository;
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
        cartAddHistoryRepository.deleteAll(cartAddHistoryRepository.findAll().stream()
            .filter(history -> history.getMemberId().equals(MEMBER_ID))
            .toList());
        cartItemRepository.deleteAll(cartItemRepository.findAllByMemberIdAndProductIdIn(
            MEMBER_ID, List.of(tofuProduct.getId())));
        autoCartItemRepository.deleteAll();
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

    private Long previewId() {
        return autoCartFacade.preview(MEMBER_ID, previewRequest(5, List.of(pepper.getId()))).autoCartRequestId();
    }

    private void assertErrorCode(Throwable thrown, Object errorCode) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("확정: 구매 가능한 상품만 장바구니에 합산하고 자동 담기 경로·요청 번호로 이력을 남긴다")
    void confirm() {
        // given
        Long autoCartRequestId = previewId();
        cartItemRepository.save(CartItem.builder()
            .memberId(MEMBER_ID)
            .productId(tofuProduct.getId())
            .quantity(1)
            .build());

        // when
        AutoCartConfirmResponse response = autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, null);

        // then
        assertThat(response.status()).isEqualTo(AutoCartStatus.CONFIRMED);
        assertThat(response.addedItems())
            .extracting(AutoCartConfirmResponse.AddedItem::addedQuantity,
                AutoCartConfirmResponse.AddedItem::cartQuantity)
            .containsExactly(tuple(2, 3));
        assertThat(response.excludedItems())
            .extracting(AutoCartConfirmResponse.ExcludedItem::reason)
            .containsExactly(UnavailableReason.NOT_SOLD_IN_MART);
        assertThat(response.priceChanged()).isFalse();

        List<CartAddHistory> histories = cartAddHistoryRepository.findAll().stream()
            .filter(history -> history.getMemberId().equals(MEMBER_ID))
            .toList();
        assertThat(histories)
            .extracting(CartAddHistory::getSource, CartAddHistory::getAutoCartRequestId,
                CartAddHistory::getQuantity)
            .containsExactly(tuple(CartAddSource.AUTO_CART, autoCartRequestId, 2));
    }

    @Test
    @DisplayName("확정 뒤 다시 조회하면 가격이 바뀌어도 확정 시점 결과를 그대로 돌려준다")
    void getAutoCart_confirmedSnapshot() {
        // given
        Long autoCartRequestId = previewId();
        autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, null);
        ReflectionTestUtils.setField(tofuProduct, "price", 9000L);
        productRepository.save(tofuProduct);

        // when
        AutoCartPreviewResponse response = autoCartFacade.getAutoCart(MEMBER_ID, autoCartRequestId);

        // then
        assertThat(response.status()).isEqualTo(AutoCartStatus.CONFIRMED);
        assertThat(response.totalPrice()).isEqualTo(5000L);
        assertThat(response.items())
            .extracting(AutoCartPreviewResponse.Item::ingredientId, AutoCartPreviewResponse.Item::unavailableReason)
            .containsExactly(tuple(tofu.getId(), null), tuple(pepper.getId(), UnavailableReason.NOT_SOLD_IN_MART));
        assertThat(response.items().get(0).expectedRemainingAmount().toPlainString()).isEqualTo("100");
    }

    @Test
    @DisplayName("확정: 미리보기 뒤 가격이 바뀌었으면 priceChanged가 true다")
    void confirm_priceChanged() {
        // given
        Long autoCartRequestId = previewId();
        ReflectionTestUtils.setField(tofuProduct, "price", 3000L);
        productRepository.save(tofuProduct);

        // when
        AutoCartConfirmResponse response = autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, null);

        // then
        assertThat(response.totalAddedPrice()).isEqualTo(6000L);
        assertThat(response.priceChanged()).isTrue();
    }

    @Test
    @DisplayName("확정: 이미 확정한 요청이면 AUTO_CART_INVALID_STATUS 예외를 던진다")
    void confirm_alreadyConfirmed() {
        // given
        Long autoCartRequestId = previewId();
        autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, null);

        // when
        Throwable thrown = catchThrowable(() -> autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, null));

        // then
        assertErrorCode(thrown, RecipeErrorCode.AUTO_CART_INVALID_STATUS);
    }

    @Test
    @DisplayName("확정: 미리보기 뒤 이용 마트가 바뀌었으면 AUTO_CART_MART_CHANGED 예외를 던진다")
    void confirm_martChanged() {
        // given
        Long autoCartRequestId = previewId();
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(mart.getId() + 1));

        // when
        Throwable thrown = catchThrowable(() -> autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, null));

        // then
        assertErrorCode(thrown, RecipeErrorCode.AUTO_CART_MART_CHANGED);
    }

    @Test
    @DisplayName("확정: 재계산 결과 담을 상품이 없으면 장바구니에 담지 않고 NO_PURCHASABLE_INGREDIENTS 예외를 던진다")
    void confirm_noPurchasable() {
        // given
        Long autoCartRequestId = previewId();
        ReflectionTestUtils.setField(tofuProduct, "totalQuantity", 0);
        productRepository.save(tofuProduct);

        // when
        Throwable thrown = catchThrowable(() -> autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, null));

        // then
        assertErrorCode(thrown, RecipeErrorCode.NO_PURCHASABLE_INGREDIENTS);
        assertThat(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, tofuProduct.getId())).isEmpty();
    }

    @Test
    @DisplayName("확정: 후보에 없는 상품을 고르면 INVALID_PRODUCT_SELECTION 예외를 던진다")
    void confirm_invalidSelection() {
        // given
        Long autoCartRequestId = previewId();
        AutoCartConfirmRequest request = new AutoCartConfirmRequest(
            List.of(new AutoCartConfirmRequest.ProductSelection(tofu.getId(), tofuProduct.getId() + 1000)));

        // when
        Throwable thrown = catchThrowable(() -> autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, request));

        // then
        assertErrorCode(thrown, RecipeErrorCode.INVALID_PRODUCT_SELECTION);
    }

    @Test
    @DisplayName("확정: 같은 요청을 5건 동시에 확정해도 장바구니에는 한 번만 합산된다")
    void confirm_concurrent() throws Exception {
        // given
        Long autoCartRequestId = previewId();
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger invalidStatus = new AtomicInteger();

        // when
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    autoCartFacade.confirm(MEMBER_ID, autoCartRequestId, null);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == RecipeErrorCode.AUTO_CART_INVALID_STATUS) {
                        invalidStatus.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // then
        assertThat(success.get()).isEqualTo(1);
        assertThat(invalidStatus.get()).isEqualTo(threads - 1);
        assertThat(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, tofuProduct.getId()))
            .get()
            .extracting(CartItem::getQuantity)
            .isEqualTo(2);
    }
}
