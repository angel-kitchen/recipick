package com.recipick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.Assertions.withMarginOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.AdminProductResponse;
import com.recipick.domain.product.dto.ProductStatusResponse;
import com.recipick.domain.product.dto.ProductStatusUpdateRequest;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MartRepository martRepository;

    @InjectMocks
    private ProductService productService;

    private final Pageable pageable = PageRequest.of(0,20);

    private Product product(Long id, Long martId) {
        Mart mart = Mart.builder()
            .regionId(1L)
            .name("삼청 마트")
            .latitude(new BigDecimal("37.5000000"))
            .longitude(new BigDecimal("127.0000000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(mart, "id", martId);
        Category category = Category.builder().name("채소").build();
        ReflectionTestUtils.setField(category, "id", 10L);

        Product product = Product.builder()
            .mart(mart)
            .category(category)
            .name("애호박")
            .price(1500L)
            .totalQuantity(5)
            .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private AdminProductSearchCondition conditionOf(Long martId){
        return new AdminProductSearchCondition(martId, null,null,null,null);
    }

    @Test
    @DisplayName("마트 관리자는 martId를 안 보내도 자기 마트로 조회된다")
    void martAdminScopedToOwnMart(){
        // given
        given(productRepository.searchAdminProducts(any(), any()))
            .willReturn(new PageImpl<>(List.of(product(1L, 1L)), pageable, 1));

        // when
        PageResponse<AdminProductResponse> result =
            productService.getAdminProducts(AdminRole.MART_ADMIN, 1L, conditionOf(null), pageable);

        // then
        ArgumentCaptor<AdminProductSearchCondition> captor =
            ArgumentCaptor.forClass(AdminProductSearchCondition.class);
        verify(productRepository).searchAdminProducts(captor.capture(), any());
        assertThat(captor.getValue().martId()).isEqualTo(1L);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).categoryName()).isEqualTo("채소");
        assertThat(result.totalElements()).isEqualTo(1);
    }
    @Test
    @DisplayName("마트 관리자가 다른 마트를 지정하면 접근이 거부된다")
    void martAdminOtherMartDenied() {
        // given
        AdminProductSearchCondition condition = conditionOf(2L);

        // when & then
        assertThatThrownBy(() ->
            productService.getAdminProducts(AdminRole.MART_ADMIN, 1L, condition, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("슈퍼 관리자는 martId를 안 보내면 전체 마트를 조회한다")
    void superAdminAllMarts() {
        // given
        given(productRepository.searchAdminProducts(any(), any()))
            .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        productService.getAdminProducts(AdminRole.SUPER_ADMIN, null, conditionOf(null), pageable);

        // then
        ArgumentCaptor<AdminProductSearchCondition> captor =
            ArgumentCaptor.forClass(AdminProductSearchCondition.class);
        verify(productRepository).searchAdminProducts(captor.capture(), any());
        assertThat(captor.getValue().martId()).isNull();
    }

    @Test
    @DisplayName("슈퍼 관리자가 없는 마트를 지정하면 MART_NOT_FOUND")
    void superAdminMartNotFound() {
        // given
        given(martRepository.existsById(99L)).willReturn(false);
        AdminProductSearchCondition condition = conditionOf(99L);

        // when & then
        assertThatThrownBy(() ->
            productService.getAdminProducts(AdminRole.SUPER_ADMIN, null, condition, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.MART_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 ID 목록으로 장바구니용 상품 정보를 조회한다")
    void findProducts() {
        // given
        Product product = product(1L, 3L);
        ReflectionTestUtils.setField(product, "status", ProductStatus.STOPPED);
        given(productRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(product));

        // when
        List<ProductSummary> result = productService.findProducts(List.of(1L, 2L));

        // then
        assertThat(result).hasSize(1);
        ProductSummary summary = result.get(0);
        assertThat(summary.productId()).isEqualTo(1L);
        assertThat(summary.martId()).isEqualTo(3L);
        assertThat(summary.name()).isEqualTo("애호박");
        assertThat(summary.price()).isEqualTo(1500L);
        assertThat(summary.status()).isEqualTo(ProductStatus.STOPPED);
        assertThat(summary.totalQuantity()).isEqualTo(5);
        assertThat(summary.deleted()).isFalse();
    }

    @Test
    @DisplayName("빈 목록이면 조회 없이 빈 결과를 돌려준다")
    void findProductsWithEmptyIds() {
        // when
        List<ProductSummary> result = productService.findProducts(List.of());

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("상태 변경 : SUPER_ADMIN이 판매 중 상품을 판매 중지로 바꾼다")
    void changeStatus_toStopped(){
        // given
        Product product = product(1L, 1L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(ProductStatus.STOPPED);

        // when
        ProductStatusResponse result =
            productService.changeStatus(AdminRole.SUPER_ADMIN, null, 1L, request);

        // then
        assertThat(result.productId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("상태 변경 : 판매 중지 상품을 다시 판매중으로 바꾼다")
    void changeStatus_toSelling(){
        //given
        Product product = product(1L, 1L);
        product.changeStatus(ProductStatus.STOPPED);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(ProductStatus.SELLING);

        //when
        ProductStatusResponse result =
            productService.changeStatus(AdminRole.SUPER_ADMIN, null, 1L, request);

        //then
        assertThat(result.status()).isEqualTo(ProductStatus.SELLING);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    @DisplayName("상태 변경 : 이미 같은 상태로 바꿔도 성공한다")
    void changeStatus_sameStatus(){
        // given
        Product product = product(1L, 1L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(ProductStatus.SELLING);

        // when
            ProductStatusResponse result =
                productService.changeStatus(AdminRole.SUPER_ADMIN, null, 1L , request);

        // then
        assertThat(result.status()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    @DisplayName("상태 변경 : MART_ADMIN은 본인 마트 상품의 상태를 바꿀 수 있다.")
    void changeStatus_martAdminOwnMart(){
        // given
        Product product = product(1L, 1L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(ProductStatus.STOPPED);

        // when
        productService.changeStatus(AdminRole.MART_ADMIN, 1L, 1L, request);

        // then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("상태 변경: MART_ADMIN이 다른 마트 상품을 바꾸면 MART_ACCESS_DENIED이고 상태는 그대로다")
    void changeStats_martAdminOtherMart(){
        Product product = product(1L, 2L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(ProductStatus.STOPPED);

        // when
        Throwable thrown = catchThrowable(
            () -> productService.changeStatus(AdminRole.MART_ADMIN, 1L, 1L, request));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    @DisplayName("상태 변경 : 없는 상품이면 PRODUCT_NOT_FOUND")
    void changeStatus_notFound() {
        // given
        given(productRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(ProductStatus.STOPPED);

        // when
        Throwable thrown = catchThrowable(
            () -> productService.changeStatus(AdminRole.SUPER_ADMIN, null, 99L, request));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상태 변경 : 삭제된 상품이면 PRODUCT_NOT_FOUND")
    void changeStatus_deleted() {
        // given
        Product product = product(1L, 1L);
        ReflectionTestUtils.setField(product, "deleted", true);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(ProductStatus.STOPPED);

        // when
        Throwable thrown = catchThrowable(
            () -> productService.changeStatus(AdminRole.SUPER_ADMIN, null, 1L, request));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    @DisplayName("삭제 : SUPER_ADMIN이 삭제하면 삭제 표시만 되고 재고·상태는 그대로다")
    void deleteProduct_superAdmin() {
        // given
        Product product = product(1L, 1L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));

        // when
        productService.deleteProduct(AdminRole.SUPER_ADMIN, null, 1L);

        // then
        assertThat(product.isDeleted()).isTrue();
        assertThat(product.getTotalQuantity()).isEqualTo(5);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    @DisplayName("삭제 : MART_ADMIN은 본인 마트 상품을 삭제할 수 있다")
    void deleteProduct_martAdminOwnMart() {
        // given
        Product product = product(1L, 1L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));

        // when
        productService.deleteProduct(AdminRole.MART_ADMIN, 1L, 1L);

        // then
        assertThat(product.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제 : MART_ADMIN이 다른 마트 상품이면 MART_ACCESS_DENIED이고 삭제되지 않는다")
    void deleteProduct_martAdminOtherMart() {
        // given
        Product product = product(1L, 2L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));

        // when
        Throwable thrown = catchThrowable(
            () -> productService.deleteProduct(AdminRole.MART_ADMIN, 1L, 1L));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        assertThat(product.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("삭제 : 없는 상품이거나 이미 삭제된 상품이면 PRODUCT_NOT_FOUND")
    void deleteProduct_notFound() {
        // given
        given(productRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        Product alreadyDeleted = product(1L, 1L);
        ReflectionTestUtils.setField(alreadyDeleted, "deleted", true);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(alreadyDeleted));

        // when
        Throwable missingThrown = catchThrowable(
            () -> productService.deleteProduct(AdminRole.SUPER_ADMIN, null, 99L));
        Throwable deletedThrown = catchThrowable(
            () -> productService.deleteProduct(AdminRole.SUPER_ADMIN, null, 1L));

        // then
        assertThat(missingThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        assertThat(deletedThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }
}
