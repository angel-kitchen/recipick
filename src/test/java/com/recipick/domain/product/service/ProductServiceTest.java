package com.recipick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.AdminProductResponse;
import com.recipick.domain.product.dto.ProductCreateRequest;
import com.recipick.domain.product.dto.ProductIdResponse;
import com.recipick.domain.product.dto.ProductStatusResponse;
import com.recipick.domain.product.dto.ProductStatusUpdateRequest;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.CategoryRepository;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;



@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MartRepository martRepository;

    @InjectMocks
    private ProductService productService;

    @Mock
    private CategoryRepository categoryRepository;

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

    private Mart mart(Long id) {
        Mart mart = Mart.builder()
            .regionId(1L)
            .name("삼청 마트")
            .latitude(new BigDecimal("37.5000000"))
            .longitude(new BigDecimal("127.0000000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(mart, "id", id);
        return mart;
    }

    private Category category(Long id) {
        Category category = Category.builder().name("채소").build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private ProductCreateRequest createRequest(Long martId, String brand, String amount, Long price) {
        return new ProductCreateRequest(
            martId, 10L, 5L, "애호박 1개", brand, "국산 애호박",
            new BigDecimal(amount), SaleUnit.G, "https://example.com/a.png", price);
    }

    private void stubMartAndCategory() {
        given(martRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mart(1L)));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category(10L)));
    }

    @Test
    @DisplayName("등록 : 성공하면 상품이 판매 중·재고 0으로 저장되고 id를 돌려준다")
    void createProduct() {
        // given
        stubMartAndCategory();
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 77L);
            return saved;
        });
        ProductCreateRequest request = createRequest(1L, "국산농장", "300.00", 1500L);

        // when
        ProductIdResponse result =
            productService.createProduct(AdminRole.SUPER_ADMIN, null, request, SaleUnit.G);

        // then
        assertThat(result.productId()).isEqualTo(77L);
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getMart().getId()).isEqualTo(1L);
        assertThat(saved.getCategory().getId()).isEqualTo(10L);
        assertThat(saved.getIngredientId()).isEqualTo(5L);
        assertThat(saved.getName()).isEqualTo("애호박 1개");
        assertThat(saved.getBrand()).isEqualTo("국산농장");
        assertThat(saved.getSaleUnit()).isEqualTo(SaleUnit.G);
        assertThat(saved.getPrice()).isEqualTo(1500L);
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.SELLING);
        assertThat(saved.getTotalQuantity()).isZero();
    }

    @Test
    @DisplayName("등록 : 재료가 없는 상품은 단위 검사 없이 브랜드 없이도 등록된다")
    void createProduct_noIngredientNoBrand() {
        // given
        stubMartAndCategory();
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));
        ProductCreateRequest request = createRequest(1L, null, "1", 1000L);

        // when
        productService.createProduct(AdminRole.SUPER_ADMIN, null, request, null);

        // then
        verify(productRepository).existsByMartIdAndNameAndBrandAndDeletedFalse(1L, "애호박 1개", null);
    }

    @Test
    @DisplayName("등록 : 재료 단위와 판매 단위가 다르면 UNIT_MISMATCH")
    void createProduct_unitMismatch() {
        // given
        stubMartAndCategory();
        ProductCreateRequest request = createRequest(1L, "국산농장", "300.00", 1500L);

        // when
        Throwable thrown = catchThrowable(
            () -> productService.createProduct(AdminRole.SUPER_ADMIN, null, request, SaleUnit.ML));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.UNIT_MISMATCH);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 : 같은 마트에 이름과 브랜드가 같은 상품이 있으면 PRODUCT_NAME_DUPLICATED")
    void createProduct_duplicated() {
        // given
        stubMartAndCategory();
        given(productRepository.existsByMartIdAndNameAndBrandAndDeletedFalse(1L, "애호박 1개", "국산농장"))
            .willReturn(true);
        ProductCreateRequest request = createRequest(1L, "국산농장", "300.00", 1500L);

        // when
        Throwable thrown = catchThrowable(
            () -> productService.createProduct(AdminRole.SUPER_ADMIN, null, request, SaleUnit.G));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NAME_DUPLICATED);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 : MART_ADMIN이 다른 마트에 등록하면 MART_ACCESS_DENIED이고 아무것도 조회하지 않는다")
    void createProduct_martAdminOtherMart() {
        // given
        ProductCreateRequest request = createRequest(2L, "국산농장", "300.00", 1500L);

        // when
        Throwable thrown = catchThrowable(
            () -> productService.createProduct(AdminRole.MART_ADMIN, 1L, request, SaleUnit.G));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        verifyNoInteractions(martRepository, categoryRepository, productRepository);
    }

    @Test
    @DisplayName("등록 : MART_ADMIN은 본인 마트에 등록할 수 있다")
    void createProduct_martAdminOwnMart() {
        // given
        stubMartAndCategory();
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));
        ProductCreateRequest request = createRequest(1L, "국산농장", "300.00", 1500L);

        // when
        productService.createProduct(AdminRole.MART_ADMIN, 1L, request, SaleUnit.G);

        // then
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("등록 : 판매 단위 수치가 0 이하면 INVALID_SALE_UNIT, 가격이 음수면 INVALID_PRICE")
    void createProduct_invalidValues() {
        // given
        ProductCreateRequest zeroAmount = createRequest(1L, null, "0", 1500L);
        ProductCreateRequest negativePrice = createRequest(1L, null, "300.00", -1L);

        // when
        Throwable amountThrown = catchThrowable(
            () -> productService.createProduct(AdminRole.SUPER_ADMIN, null, zeroAmount, SaleUnit.G));
        Throwable priceThrown = catchThrowable(
            () -> productService.createProduct(AdminRole.SUPER_ADMIN, null, negativePrice, SaleUnit.G));

        // then
        assertThat(amountThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_SALE_UNIT);
        assertThat(priceThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_PRICE);
        verifyNoInteractions(martRepository, categoryRepository);
    }

    @Test
    @DisplayName("등록 : 없는 마트이면 MART_NOT_FOUND")
    void createProduct_martNotFound() {
        // given
        given(martRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        ProductCreateRequest request = createRequest(99L, null, "300.00", 1500L);

        // when
        Throwable thrown = catchThrowable(
            () -> productService.createProduct(AdminRole.SUPER_ADMIN, null, request, SaleUnit.G));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.MART_NOT_FOUND);
    }

    @Test
    @DisplayName("등록 : 없는 카테고리이거나 삭제된 카테고리이면 CATEGORY_NOT_FOUND")
    void createProduct_categoryNotFound() {
        // given
        given(martRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mart(1L)));
        Category deleted = category(10L);
        deleted.delete();
        given(categoryRepository.findById(10L)).willReturn(Optional.of(deleted));
        given(categoryRepository.findById(11L)).willReturn(Optional.empty());
        ProductCreateRequest deletedCategory = createRequest(1L, null, "300.00", 1500L);
        ProductCreateRequest missingCategory = new ProductCreateRequest(
            1L, 11L, 5L, "애호박 1개", null, null,
            new BigDecimal("300.00"), SaleUnit.G, null, 1500L);

        // when
        Throwable deletedThrown = catchThrowable(
            () -> productService.createProduct(AdminRole.SUPER_ADMIN, null, deletedCategory, SaleUnit.G));
        Throwable missingThrown = catchThrowable(
            () -> productService.createProduct(AdminRole.SUPER_ADMIN, null, missingCategory, SaleUnit.G));

        // then
        assertThat(deletedThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);
        assertThat(missingThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);
    }
}
