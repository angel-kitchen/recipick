package com.recipick.domain.product.service;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.ProductCreateRequest;
import com.recipick.domain.product.dto.ProductIdResponse;
import com.recipick.domain.product.dto.ProductStatusResponse;
import com.recipick.domain.product.dto.ProductStatusUpdateRequest;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.CategoryRepository;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.dto.AdminProductResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final MartRepository martRepository;
    private final CategoryRepository categoryRepository;

    public PageResponse<AdminProductResponse> getAdminProducts(
        AdminRole role,
        Long adminMartId,
        AdminProductSearchCondition condition,
        Pageable pageable
    ) {
        Long martId = resolveMartId(role, adminMartId, condition.martId());
        AdminProductSearchCondition scoped = new AdminProductSearchCondition(
            martId,
            condition.keyword(),
            condition.categoryId(),
            condition.ingredientId(),
            condition.status()
        );

        return PageResponse.from(
            productRepository.searchAdminProducts(scoped, pageable)
                .map(AdminProductResponse::from)
        );
    }

    @Transactional
    public ProductStatusResponse changeStatus(
        AdminRole role,
        Long adminMartId,
        Long productId,
        ProductStatusUpdateRequest request
    ){
        Product product = productRepository.findByIdForUpdate(productId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if(role == AdminRole.MART_ADMIN && !product.getMart().getId().equals(adminMartId)){
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }

        product.changeStatus(request.status());
        return ProductStatusResponse.from(product);
    }

    @Transactional
    public void deleteProduct(AdminRole role, Long adminMartId, Long productId){
        Product product = productRepository.findByIdForUpdate(productId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if(role == AdminRole.MART_ADMIN && !product.getMart().getId().equals(adminMartId)){
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }

        product.delete();
    }

    // 장바구니처럼 여러 상품을 한 화면에 그리는 곳에서 쓰므로 IN 쿼리 한 번으로 조회한다. 없는 ID는 결과에서 빠진다.
    public List<ProductSummary> findProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productRepository.findAllById(productIds).stream()
            .map(ProductSummary::from)
            .toList();
    }

    private Long resolveMartId(AdminRole role, Long adminMartId, Long requestedMartId){
        if (role == AdminRole.MART_ADMIN){
            if(requestedMartId != null && !requestedMartId.equals(adminMartId)){
                throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
            }
            return adminMartId;
        }

        if(requestedMartId != null && !martRepository.existsById(requestedMartId)){
            throw new BusinessException(ProductErrorCode.MART_NOT_FOUND);
        }
        return requestedMartId;
    }

    @Transactional
    public ProductIdResponse createProduct(
        AdminRole role,
        Long adminMartId,
        ProductCreateRequest request,
        SaleUnit ingredientUnit
    ) {
        if (role == AdminRole.MART_ADMIN && !request.martId().equals(adminMartId)) {
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }
        if (request.saleUnitAmount().signum() <= 0) {
            throw new BusinessException(ProductErrorCode.INVALID_SALE_UNIT);
        }
        if (request.price() < 0) {
            throw new BusinessException(ProductErrorCode.INVALID_PRICE);
        }

        Mart mart = martRepository.findByIdForUpdate(request.martId())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.MART_NOT_FOUND));
        Category category = categoryRepository.findById(request.categoryId())
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));

        if (ingredientUnit != null && ingredientUnit != request.saleUnit()) {
            throw new BusinessException(ProductErrorCode.UNIT_MISMATCH);
        }
        if (productRepository.existsByMartIdAndNameAndBrandAndDeletedFalse(
            mart.getId(), request.name(), request.brand())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        Product product = Product.builder()
            .mart(mart)
            .category(category)
            .ingredientId(request.ingredientId())
            .name(request.name())
            .brand(request.brand())
            .description(request.description())
            .saleUnitAmount(request.saleUnitAmount())
            .saleUnit(request.saleUnit())
            .imageUrl(request.imageUrl())
            .price(request.price())
            .build();
        return new ProductIdResponse(productRepository.save(product).getId());
    }

}
