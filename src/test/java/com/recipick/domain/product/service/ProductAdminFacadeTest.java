package com.recipick.domain.product.service;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.ProductCreateRequest;
import com.recipick.domain.product.dto.ProductIdResponse;
import com.recipick.domain.product.dto.ProductUpdateRequest;
import com.recipick.domain.product.facade.ProductAdminFacade;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.service.IngredientService;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductAdminFacadeTest {

    @Mock
    private IngredientService ingredientService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductAdminFacade productAdminFacade;

    private ProductCreateRequest request(Long ingredientId) {
        return new ProductCreateRequest(
            1L, 10L, ingredientId, "애호박 1개", null, null,
            new BigDecimal("300.00"), SaleUnit.G, null, 1500L);
    }

    @Test
    @DisplayName("등록 : 재료가 있으면 재료 단위를 상품 서비스에 넘긴다")
    void createProduct_withIngredient() {
        // given
        ProductCreateRequest request = request(5L);
        Ingredient ingredient = Ingredient.builder().name("애호박").unit(SaleUnit.G).build();
        given(ingredientService.getIngredient(5L)).willReturn(ingredient);
        given(productService.createProduct(AdminRole.SUPER_ADMIN, null, request, SaleUnit.G))
            .willReturn(new ProductIdResponse(77L));

        // when
        ProductIdResponse result = productAdminFacade.createProduct(AdminRole.SUPER_ADMIN, null, request);

        // then
        assertThat(result.productId()).isEqualTo(77L);
        verify(productService).createProduct(AdminRole.SUPER_ADMIN, null, request, SaleUnit.G);
    }

    @Test
    @DisplayName("등록 : 재료를 지정하지 않으면 재료 서비스를 부르지 않고 단위는 null로 넘긴다")
    void createProduct_withoutIngredient() {
        // given
        ProductCreateRequest request = request(null);
        given(productService.createProduct(AdminRole.MART_ADMIN, 1L, request, null))
            .willReturn(new ProductIdResponse(78L));

        // when
        ProductIdResponse result = productAdminFacade.createProduct(AdminRole.MART_ADMIN, 1L, request);

        // then
        assertThat(result.productId()).isEqualTo(78L);
        verifyNoInteractions(ingredientService);
    }

    @Test
    @DisplayName("등록 : 없는 재료이면 INGREDIENT_NOT_FOUND이고 상품을 만들지 않는다")
    void createProduct_ingredientNotFound() {
        // given
        ProductCreateRequest request = request(99L);
        given(ingredientService.getIngredient(99L))
            .willThrow(new BusinessException(RecipeErrorCode.INGREDIENT_NOT_FOUND));

        // when
        Throwable thrown = catchThrowable(
            () -> productAdminFacade.createProduct(AdminRole.SUPER_ADMIN, null, request));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INGREDIENT_NOT_FOUND);
        verifyNoInteractions(productService);
    }

    private ProductUpdateRequest updateRequest(Long ingredientId) {
        return new ProductUpdateRequest(
            10L, ingredientId, "애호박 대", null, null,
            new BigDecimal("350.00"), SaleUnit.G, null);
    }

    @Test
    @DisplayName("수정 : 재료가 있으면 재료 단위를 상품 서비스에 넘긴다")
    void updateProduct_withIngredient() {
        // given
        ProductUpdateRequest request = updateRequest(5L);
        Ingredient ingredient = Ingredient.builder().name("애호박").unit(SaleUnit.G).build();
        given(ingredientService.getIngredient(5L)).willReturn(ingredient);
        given(productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request, SaleUnit.G))
            .willReturn(new ProductIdResponse(1L));

        // when
        ProductIdResponse result =
            productAdminFacade.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request);

        // then
        assertThat(result.productId()).isEqualTo(1L);
        verify(productService).updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request, SaleUnit.G);
    }

    @Test
    @DisplayName("수정 : 재료를 null로 보내면 재료 서비스를 부르지 않고 단위 null로 넘겨 연결을 해제한다")
    void updateProduct_clearIngredient() {
        // given
        ProductUpdateRequest request = updateRequest(null);
        given(productService.updateProduct(AdminRole.MART_ADMIN, 1L, 1L, request, null))
            .willReturn(new ProductIdResponse(1L));

        // when
        productAdminFacade.updateProduct(AdminRole.MART_ADMIN, 1L, 1L, request);

        // then
        verifyNoInteractions(ingredientService);
        verify(productService).updateProduct(AdminRole.MART_ADMIN, 1L, 1L, request, null);
    }

    @Test
    @DisplayName("수정 : 없는 재료이면 INGREDIENT_NOT_FOUND이고 상품을 수정하지 않는다")
    void updateProduct_ingredientNotFound() {
        // given
        ProductUpdateRequest request = updateRequest(99L);
        given(ingredientService.getIngredient(99L))
            .willThrow(new BusinessException(RecipeErrorCode.INGREDIENT_NOT_FOUND));

        // when
        Throwable thrown = catchThrowable(
            () -> productAdminFacade.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INGREDIENT_NOT_FOUND);
        verifyNoInteractions(productService);
    }
}
