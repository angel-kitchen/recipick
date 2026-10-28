package com.recipick.global.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.order.dto.request.OrderAdminSearchRequest;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.product.dto.*;
import com.recipick.domain.product.entity.*;
import com.recipick.domain.product.repository.*;
import com.recipick.domain.product.service.*;
import com.recipick.domain.recipe.dto.request.*;
import com.recipick.domain.recipe.entity.*;
import com.recipick.domain.recipe.facade.RecipeAdminFacade;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.domain.recipe.service.IngredientService;
import com.recipick.domain.recipe.service.RecipeAdminService;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminAuditConnectionIntegrationTest extends IntegrationTestSupport {
    @Autowired AdminRepository admins;
    @Autowired MartRepository marts;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired IngredientRepository ingredients;
    @Autowired RecipeRepository recipes;
    @Autowired OrderRepository orders;
    @Autowired com.recipick.domain.order.repository.OrderItemRepository orderItems;
    @Autowired CategoryService categoryService;
    @Autowired ProductService productService;
    @Autowired ProductStockService stocks;
    @Autowired MartService martService;
    @Autowired IngredientService ingredientService;
    @Autowired RecipeAdminService recipeService;
    @Autowired RecipeAdminFacade recipeFacade;
    @Autowired OrderService orderService;
    @Autowired com.recipick.domain.order.facade.OrderQueryFacade orderQueryFacade;
    @Autowired com.recipick.domain.recipe.facade.IngredientAdminFacade ingredientFacade;
    @Autowired JdbcTemplate jdbc;
    Admin admin;
    Mart mart;
    Category category;
    Product product;
    Ingredient ingredient;
    Recipe recipe;
    Long orderId;
    List<Long> extraCategories = new ArrayList<>();
    List<Long> extraRecipes = new ArrayList<>();

    @BeforeEach
    void setup() {
        admin = admins.saveAndFlush(Admin.builder().loginId(UUID.randomUUID().toString()).passwordHash("hash")
                .role(AdminRole.SUPER_ADMIN).active(true).build());
        mart = marts.saveAndFlush(Mart.builder().regionId(Math.abs(UUID.randomUUID().getMostSignificantBits()))
                .name("감사 연결 마트").latitude(new BigDecimal("37.5")).longitude(new BigDecimal("127.0"))
                .serviceRadiusM(2000).build());
        category = categories.saveAndFlush(Category.builder().name(UUID.randomUUID().toString()).build());
        product = products.saveAndFlush(Product.builder().mart(mart).category(category).name("테스트 상품")
                .price(1000L).saleUnit(SaleUnit.G).saleUnitAmount(new BigDecimal("100"))
                .totalQuantity(10).build());
        ingredient = ingredients.saveAndFlush(Ingredient.builder().name(UUID.randomUUID().toString()).unit(SaleUnit.G).build());
        recipe = recipes.saveAndFlush(Recipe.builder().name("테스트 요리").baseServings(2).steps(List.of()).build());
        AuthPrincipal principal = new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "SUPER_ADMIN", null);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", admin.getId());
        if (orderId != null) {
            jdbc.update("DELETE FROM order_status_history WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM order_item WHERE order_id = ?", orderId);
            orders.deleteById(orderId);
        }
        jdbc.update("DELETE FROM stock_adjustment WHERE product_id = ?", product.getId());
        products.deleteById(product.getId());
        marts.deleteById(mart.getId());
        jdbc.update("DELETE FROM recipe_ingredient WHERE recipe_id = ?", recipe.getId());
        recipes.deleteById(recipe.getId());
        recipes.deleteAllById(extraRecipes);
        ingredients.deleteById(ingredient.getId());
        categories.deleteById(category.getId());
        categories.deleteAllById(extraCategories);
        admins.deleteById(admin.getId());
    }

    private List<String> actions() {
        return jdbc.queryForList("SELECT action FROM admin_audit_log WHERE admin_id = ? ORDER BY id", String.class, admin.getId());
    }

    @Test
    @DisplayName("카테고리의 실제 변경·복구만 기록하고 같은 이름 수정은 기록하지 않는다")
    void recordsCategoryChangesAndRestore() {
        // given
        String name = UUID.randomUUID().toString();
        Long id = categoryService.createCategory(new CategoryCreateRequest(name)).categoryId();
        extraCategories.add(id);
        // when
        categoryService.updateCategory(id, new CategoryUpdateRequest(name));
        categoryService.updateCategory(id, new CategoryUpdateRequest(name + "x"));
        categoryService.delete(id);
        categoryService.createCategory(new CategoryCreateRequest(name + "x"));
        // then
        assertThat(actions()).containsExactly("CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DELETE", "CATEGORY_CREATE");
    }

    @Test
    @DisplayName("상품 수정·가격·재고·판매 상태·삭제에 마트와 전후 값을 기록한다")
    void recordsProductChanges() {
        // given
        var same = new ProductUpdateRequest(category.getId(), null, "테스트 상품", null, null,
                new BigDecimal("100"), SaleUnit.G, null);
        productService.updateProduct(AdminRole.SUPER_ADMIN, null, product.getId(), same, null);
        // when
        productService.updateProduct(AdminRole.SUPER_ADMIN, null, product.getId(),
                new ProductUpdateRequest(category.getId(), null, "변경 상품", null, null,
                        new BigDecimal("200"), SaleUnit.G, null), null);
        productService.updatePrice(AdminRole.SUPER_ADMIN, null, product.getId(), new ProductPriceUpdateRequest(2000L));
        productService.updatePrice(AdminRole.SUPER_ADMIN, null, product.getId(), new ProductPriceUpdateRequest(2000L));
        stocks.adjustStock(AdminRole.SUPER_ADMIN, null, admin.getId(), product.getId(),
                new StockAdjustmentRequest(StockAdjustmentType.INCREASE, 2, "기록하면 안 되는 자유 입력 원문"));
        productService.changeStatus(AdminRole.SUPER_ADMIN, null, product.getId(), new ProductStatusUpdateRequest(ProductStatus.STOPPED));
        productService.deleteProduct(AdminRole.SUPER_ADMIN, null, product.getId());
        // then
        assertThat(actions()).containsExactly("PRODUCT_UPDATE", "PRODUCT_PRICE_CHANGE", "STOCK_ADJUST", "PRODUCT_STATUS_CHANGE", "PRODUCT_DELETE");
        assertThat(jdbc.queryForList("SELECT mart_id FROM admin_audit_log WHERE admin_id = ?", Long.class, admin.getId()))
                .containsOnly(mart.getId());
        assertThat(jdbc.queryForList("SELECT CAST(details AS CHAR) FROM admin_audit_log WHERE admin_id = ?", String.class, admin.getId()))
                .allSatisfy(detail -> assertThat(detail).doesNotContain("자유 입력", "변경 상품"));
    }

    @Test
    @DisplayName("마트 운영 상태 변경은 한 번만 기록한다")
    void recordsMartStatus() {
        // given
        var request = new MartUpdateRequest(null, null, null, MartStatus.CLOSED);
        // when
        martService.updateMart(AdminRole.SUPER_ADMIN, null, mart.getId(), request);
        martService.updateMart(AdminRole.SUPER_ADMIN, null, mart.getId(), request);
        // then
        assertThat(actions()).containsExactly("MART_STATUS_CHANGE");
    }

    @Test
    @DisplayName("레시피 등록·수정·상태와 재료 수정을 기록한다")
    void recordsRecipeAndIngredient() {
        // given
        var request = new RecipeSaveRequest("테스트 요리", CuisineType.values()[0], DishType.values()[0],
                2, 10, Difficulty.values()[0], 0, null, List.of(new RecipeStepRequest(1, "섞기")));
        Long id = recipeService.createRecipe(request).recipeId();
        extraRecipes.add(id);
        recipeService.updateRecipe(id, request);
        // when
        recipeService.updateRecipe(id, new RecipeSaveRequest("변경된 요리", request.cuisineType(), request.dishType(),
                2, 10, request.difficulty(), 0, null, request.steps()));
        recipeService.changeStatus(id, new RecipeStatusUpdateRequest(RecipeStatus.INACTIVE));
        ingredientService.updateIngredient(ingredient.getId(), new IngredientSaveRequest("바뀐 재료", SaleUnit.G));
        ingredientService.updateIngredient(ingredient.getId(), new IngredientSaveRequest("바뀐 재료", SaleUnit.G));
        // then
        assertThat(actions()).containsExactly("RECIPE_CREATE", "RECIPE_UPDATE", "RECIPE_STATUS_CHANGE", "INGREDIENT_UPDATE");
    }

    @Test
    @DisplayName("레시피 재료 변경과 관리자 자동 장보기 지정만 기록하고 동일 요청은 생략한다")
    void recordsRecipeIngredientsAndAutoCart() {
        // given
        var request = new RecipeIngredientsSaveRequest(List.of(new RecipeIngredientsSaveRequest.Item(
                ingredient.getId(), new BigDecimal("10"), true)));
        // when
        recipeFacade.saveIngredients(recipe.getId(), request);
        recipeFacade.saveIngredients(recipe.getId(), request);
        productService.updateProduct(AdminRole.SUPER_ADMIN, null, product.getId(),
                new ProductUpdateRequest(category.getId(), ingredient.getId(), "테스트 상품", null, null,
                        new BigDecimal("100"), SaleUnit.G, null), SaleUnit.G);
        recipeFacade.changeAutoCart(recipe.getId(), new RecipeAutoCartUpdateRequest(true));
        recipeFacade.changeAutoCart(recipe.getId(), new RecipeAutoCartUpdateRequest(true));
        // then
        assertThat(actions()).containsExactly("RECIPE_INGREDIENTS_CHANGE", "PRODUCT_UPDATE", "RECIPE_AUTO_CART_CHANGE");
    }

    @Test
    @DisplayName("주문 개인정보 조회는 매번 기록하고 수락 재전송은 기록을 중복하지 않는다")
    void recordsOrderViewsAndAcceptOnce() {
        // given
        orderId = orders.saveAndFlush(Order.builder().memberId(999L).martId(mart.getId())
                .status(OrderStatus.PAID).martStatus(com.recipick.domain.order.entity.enums.MartStatus.RECEIVED).build()).getId();
        orderItems.saveAndFlush(com.recipick.domain.order.entity.OrderItem.builder()
                .order(orders.findById(orderId).orElseThrow()).productId(product.getId()).productName("주문 상품")
                .unitPrice(1000L).saleUnitAmount(new BigDecimal("100")).saleUnit(SaleUnit.G)
                .lineAmount(1000L).quantity(1).build());
        // when
        orderQueryFacade.searchAdminOrders(AdminRole.SUPER_ADMIN, null, new OrderAdminSearchRequest(null, mart.getId()), PageRequest.of(0, 20));
        orderService.getAdminOrder(AdminRole.SUPER_ADMIN, null, orderId);
        orderService.getAdminOrder(AdminRole.SUPER_ADMIN, null, orderId);
        orderService.acceptByMart(AdminRole.SUPER_ADMIN, null, admin.getId(), orderId);
        orderService.acceptByMart(AdminRole.SUPER_ADMIN, null, admin.getId(), orderId);
        // then
        assertThat(actions()).containsExactly("ORDER_LIST_VIEW", "ORDER_DETAIL_VIEW", "ORDER_DETAIL_VIEW", "ORDER_ACCEPT");
    }

    @Test
    @DisplayName("감사 기록이 실패하면 먼저 변경한 상품 가격도 롤백한다")
    void rollsBackWhenAuditFails() {
        // given
        SecurityContextHolder.clearContext();
        // when, then
        assertThatThrownBy(() -> productService.updatePrice(AdminRole.SUPER_ADMIN, null, product.getId(),
                new ProductPriceUpdateRequest(9999L))).isInstanceOf(BusinessException.class);
        assertThat(products.findById(product.getId()).orElseThrow().getPrice()).isEqualTo(1000L);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"category", "ingredient"})
    @DisplayName("같은 값의 카테고리·재료 동시 수정은 잠금 후 비교해 감사 기록을 한 건만 남긴다")
    void concurrentSameValueUpdatesRecordOnce(String target) throws Exception {
        // given
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        var start = new java.util.concurrent.CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<?>>();
        String updatedName = UUID.randomUUID().toString();
        try {
            // when
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    var principal = new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "SUPER_ADMIN", null);
                    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, principal.authorities()));
                    try {
                        start.await();
                        if (target.equals("category")) {
                            categoryService.updateCategory(category.getId(), new CategoryUpdateRequest(updatedName));
                        } else {
                            ingredientFacade.updateIngredient(ingredient.getId(), new IngredientSaveRequest(updatedName, SaleUnit.G));
                        }
                        return null;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(15, java.util.concurrent.TimeUnit.SECONDS);
            }
            // then
            assertThat(actions()).containsExactly(target.equals("category") ? "CATEGORY_UPDATE" : "INGREDIENT_UPDATE");
        } finally {
            executor.shutdownNow();
        }
    }

}
