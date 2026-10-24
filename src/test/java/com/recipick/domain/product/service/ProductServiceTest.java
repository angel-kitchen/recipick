package com.recipick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.AdminProductResponse;
import com.recipick.domain.product.dto.ProductCreateRequest;
import com.recipick.domain.product.dto.ProductDetailResponse;
import com.recipick.domain.product.dto.ProductIdResponse;
import com.recipick.domain.product.dto.ProductPriceResponse;
import com.recipick.domain.product.dto.ProductPriceUpdateRequest;
import com.recipick.domain.product.dto.ProductSearchRequest;
import com.recipick.domain.product.dto.ProductSearchResponse;
import com.recipick.domain.product.dto.ProductStatusResponse;
import com.recipick.domain.product.dto.ProductStatusUpdateRequest;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.dto.ProductUpdateRequest;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.CategoryRepository;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import com.recipick.domain.product.repository.dto.MemberProductSearchCondition;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
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
import org.springframework.data.domain.Sort;
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

    private ProductUpdateRequest updateRequest(String name, String brand, Long ingredientId,
                                               SaleUnit saleUnit, String amount) {
        return new ProductUpdateRequest(
            10L, ingredientId, name, brand, "수정된 설명",
            new BigDecimal(amount), saleUnit, "https://example.com/new.png");
    }

    private void stubProductAndCategory(Product product) {
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        given(categoryRepository.findById(10L)).willReturn(Optional.of(category(10L)));
    }

    @Test
    @DisplayName("수정 : 요청 값으로 전체가 덮어써지고 가격·상태·재고는 그대로다")
    void updateProduct() {
        // given
        Product product = product(1L, 1L);
        stubProductAndCategory(product);
        ProductUpdateRequest request = updateRequest("애호박 대", "국산농장", 5L, SaleUnit.G, "350.00");

        // when
        ProductIdResponse result =
            productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request, SaleUnit.G);

        // then
        assertThat(result.productId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("애호박 대");
        assertThat(product.getBrand()).isEqualTo("국산농장");
        assertThat(product.getIngredientId()).isEqualTo(5L);
        assertThat(product.getSaleUnit()).isEqualTo(SaleUnit.G);
        assertThat(product.getSaleUnitAmount()).isEqualByComparingTo("350.00");
        assertThat(product.getDescription()).isEqualTo("수정된 설명");
        assertThat(product.getPrice()).isEqualTo(1500L);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
        assertThat(product.getTotalQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("수정 : 재료·브랜드·설명·이미지를 null로 보내면 비워진다")
    void updateProduct_clearOptionalFields() {
        // given
        Product product = product(1L, 1L);
        product.updateInfo(category(10L), 5L, "애호박", "국산농장", "설명",
            new BigDecimal("300.00"), SaleUnit.G, "https://example.com/old.png");
        stubProductAndCategory(product);
        ProductUpdateRequest request = new ProductUpdateRequest(
            10L, null, "애호박", null, null, new BigDecimal("300.00"), SaleUnit.G, null);

        // when
        productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request, null);

        // then
        assertThat(product.getIngredientId()).isNull();
        assertThat(product.getBrand()).isNull();
        assertThat(product.getDescription()).isNull();
        assertThat(product.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("수정 : 이름과 브랜드가 그대로면 중복 검사도 마트 잠금도 하지 않는다")
    void updateProduct_sameNameAndBrand() {
        // given
        Product product = product(1L, 1L);
        stubProductAndCategory(product);
        ProductUpdateRequest request = updateRequest("애호박", null, null, SaleUnit.G, "300.00");

        // when
        productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request, null);

        // then
        verify(martRepository, never()).findByIdForUpdate(any());
        verify(productRepository, never())
            .existsByMartIdAndNameAndBrandAndDeletedFalseAndIdNot(any(), any(), any(), any());
    }

    @Test
    @DisplayName("수정 : 다른 상품과 이름·브랜드가 겹치면 PRODUCT_NAME_DUPLICATED이고 바뀌지 않는다")
    void updateProduct_duplicated() {
        // given
        Product product = product(1L, 1L);
        stubProductAndCategory(product);
        given(productRepository.existsByMartIdAndNameAndBrandAndDeletedFalseAndIdNot(1L, "청양고추", null, 1L))
            .willReturn(true);
        ProductUpdateRequest request = updateRequest("청양고추", null, null, SaleUnit.G, "300.00");

        // when
        Throwable thrown = catchThrowable(
            () -> productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request, null));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NAME_DUPLICATED);
        assertThat(product.getName()).isEqualTo("애호박");
    }

    @Test
    @DisplayName("수정 : 재료 단위와 판매 단위가 다르면 UNIT_MISMATCH")
    void updateProduct_unitMismatch() {
        // given
        Product product = product(1L, 1L);
        stubProductAndCategory(product);
        ProductUpdateRequest request = updateRequest("애호박", null, 5L, SaleUnit.G, "300.00");

        // when
        Throwable thrown = catchThrowable(
            () -> productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request, SaleUnit.ML));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.UNIT_MISMATCH);
    }

    @Test
    @DisplayName("수정 : 판매 단위 수치가 0 이하면 INVALID_SALE_UNIT, 없는·삭제된 카테고리면 CATEGORY_NOT_FOUND")
    void updateProduct_invalidValues() {
        // given
        Product product = product(1L, 1L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        Category deleted = category(10L);
        deleted.delete();
        given(categoryRepository.findById(10L)).willReturn(Optional.of(deleted));
        ProductUpdateRequest zeroAmount = updateRequest("애호박", null, null, SaleUnit.G, "0");
        ProductUpdateRequest deletedCategory = updateRequest("애호박", null, null, SaleUnit.G, "300.00");

        // when
        Throwable amountThrown = catchThrowable(
            () -> productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, zeroAmount, null));
        Throwable categoryThrown = catchThrowable(
            () -> productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, deletedCategory, null));

        // then
        assertThat(amountThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_SALE_UNIT);
        assertThat(categoryThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("수정 : MART_ADMIN이 다른 마트 상품이면 MART_ACCESS_DENIED이고 카테고리를 조회하지 않는다")
    void updateProduct_martAdminOtherMart() {
        // given
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product(1L, 2L)));
        ProductUpdateRequest request = updateRequest("애호박", null, null, SaleUnit.G, "300.00");

        // when
        Throwable thrown = catchThrowable(
            () -> productService.updateProduct(AdminRole.MART_ADMIN, 1L, 1L, request, null));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("수정 : 없는 상품이거나 삭제된 상품이면 PRODUCT_NOT_FOUND")
    void updateProduct_notFound() {
        // given
        given(productRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        Product deleted = product(1L, 1L);
        ReflectionTestUtils.setField(deleted, "deleted", true);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(deleted));
        ProductUpdateRequest request = updateRequest("애호박", null, null, SaleUnit.G, "300.00");

        // when
        Throwable missingThrown = catchThrowable(
            () -> productService.updateProduct(AdminRole.SUPER_ADMIN, null, 99L, request, null));
        Throwable deletedThrown = catchThrowable(
            () -> productService.updateProduct(AdminRole.SUPER_ADMIN, null, 1L, request, null));

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

    @Test
    @DisplayName("가격 변경 : 가격이 바뀌고 응답에 변경된 가격이 담긴다")
    void updatePrice() {
        // given
        Product product = product(1L, 1L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));

        // when
        ProductPriceResponse result = productService.updatePrice(
            AdminRole.MART_ADMIN, 1L, 1L, new ProductPriceUpdateRequest(1800L));

        // then
        assertThat(result.productId()).isEqualTo(1L);
        assertThat(result.price()).isEqualTo(1800L);
        assertThat(product.getPrice()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("가격 변경 : 가격이 음수면 INVALID_PRICE이고 가격은 그대로다")
    void updatePrice_negative() {
        // given
        Product product = product(1L, 1L);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));

        // when
        Throwable thrown = catchThrowable(() -> productService.updatePrice(
            AdminRole.SUPER_ADMIN, null, 1L, new ProductPriceUpdateRequest(-1L)));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_PRICE);
        assertThat(product.getPrice()).isEqualTo(1500L);
    }

    @Test
    @DisplayName("가격 변경 : MART_ADMIN이 다른 마트 상품이면 MART_ACCESS_DENIED, 없는 상품이면 PRODUCT_NOT_FOUND")
    void updatePrice_deniedAndNotFound() {
        // given
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product(1L, 2L)));
        given(productRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        ProductPriceUpdateRequest request = new ProductPriceUpdateRequest(1800L);

        // when
        Throwable deniedThrown = catchThrowable(
            () -> productService.updatePrice(AdminRole.MART_ADMIN, 1L, 1L, request));
        Throwable missingThrown = catchThrowable(
            () -> productService.updatePrice(AdminRole.SUPER_ADMIN, null, 99L, request));

        // then
        assertThat(deniedThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        assertThat(missingThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    private ProductSearchRequest searchRequest(Long minPrice, Long maxPrice) {
        return new ProductSearchRequest("호박", 10L, minPrice, maxPrice);
    }

    @Test
    @DisplayName("회원 검색 : 이용 마트와 조건으로 조회하고 품절 여부를 계산해 돌려준다")
    void searchProducts() {
        // given
        Product inStock = product(1L, 1L);
        Product soldOut = product(2L, 1L);
        ReflectionTestUtils.setField(soldOut, "totalQuantity", 0);
        given(productRepository.searchMemberProducts(any(), any()))
            .willReturn(new PageImpl<>(List.of(inStock, soldOut), pageable, 2));

        // when
        PageResponse<ProductSearchResponse> result =
            productService.searchProducts(1L, searchRequest(500L, 3000L), pageable);

        // then
        ArgumentCaptor<MemberProductSearchCondition> captor =
            ArgumentCaptor.forClass(MemberProductSearchCondition.class);
        verify(productRepository).searchMemberProducts(captor.capture(), any());
        assertThat(captor.getValue().martId()).isEqualTo(1L);
        assertThat(captor.getValue().keyword()).isEqualTo("호박");
        assertThat(captor.getValue().categoryId()).isEqualTo(10L);
        assertThat(captor.getValue().minPrice()).isEqualTo(500L);
        assertThat(captor.getValue().maxPrice()).isEqualTo(3000L);
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).soldOut()).isFalse();
        assertThat(result.content().get(1).soldOut()).isTrue();
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("회원 검색 : 최소 가격이 최대 가격보다 크면 INVALID_INPUT이고 조회하지 않는다")
    void searchProducts_invalidPriceRange() {
        // given
        ProductSearchRequest request = searchRequest(3000L, 1000L);

        // when
        Throwable thrown = catchThrowable(() -> productService.searchProducts(1L, request, pageable));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.INVALID_INPUT);
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("회원 검색 : 허용되지 않는 정렬 기준이면 INVALID_INPUT, 가격·등록일 정렬은 허용된다")
    void searchProducts_sort() {
        // given
        given(productRepository.searchMemberProducts(any(), any()))
            .willReturn(new PageImpl<>(List.of(), pageable, 0));
        Pageable invalidSort = PageRequest.of(0, 20, Sort.by("name"));
        Pageable priceSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "price"));
        Pageable createdSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        ProductSearchRequest request = searchRequest(null, null);

        // when
        Throwable thrown = catchThrowable(() -> productService.searchProducts(1L, request, invalidSort));
        productService.searchProducts(1L, request, priceSort);
        productService.searchProducts(1L, request, createdSort);

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.INVALID_INPUT);
        verify(productRepository).searchMemberProducts(any(), eq(priceSort));
        verify(productRepository).searchMemberProducts(any(), eq(createdSort));
    }

    @Test
    @DisplayName("회원 상세 : 이용 마트의 상품이면 카테고리명과 품절 여부를 포함해 돌려준다")
    void getProduct() {
        // given
        given(productRepository.findById(1L)).willReturn(Optional.of(product(1L, 1L)));

        // when
        ProductDetailResponse result = productService.getProduct(1L, 1L);

        // then
        assertThat(result.productId()).isEqualTo(1L);
        assertThat(result.categoryName()).isEqualTo("채소");
        assertThat(result.price()).isEqualTo(1500L);
        assertThat(result.soldOut()).isFalse();
    }

    @Test
    @DisplayName("회원 상세 : 없는 상품·삭제된 상품·다른 마트 상품이면 PRODUCT_NOT_FOUND")
    void getProduct_notFound() {
        // given
        given(productRepository.findById(99L)).willReturn(Optional.empty());
        Product deleted = product(2L, 1L);
        ReflectionTestUtils.setField(deleted, "deleted", true);
        given(productRepository.findById(2L)).willReturn(Optional.of(deleted));
        given(productRepository.findById(3L)).willReturn(Optional.of(product(3L, 2L)));

        // when
        Throwable missing = catchThrowable(() -> productService.getProduct(1L, 99L));
        Throwable deletedThrown = catchThrowable(() -> productService.getProduct(1L, 2L));
        Throwable otherMart = catchThrowable(() -> productService.getProduct(1L, 3L));

        // then
        assertThat(missing).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        assertThat(deletedThrown).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        assertThat(otherMart).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }
}
