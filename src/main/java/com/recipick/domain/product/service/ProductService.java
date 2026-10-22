package com.recipick.domain.product.service;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.ProductStatusResponse;
import com.recipick.domain.product.dto.ProductStatusUpdateRequest;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductMartId;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.dto.AdminProductResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final MartRepository martRepository;

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

    // 장바구니처럼 여러 상품을 한 화면에 그리는 곳에서 쓰므로 IN 쿼리 한 번으로 조회한다. 없는 ID는 결과에서 빠진다.
    public List<ProductSummary> findProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productRepository.findAllById(productIds).stream()
            .map(ProductSummary::from)
            .toList();
    }

    // 엔티티를 읽지 않고 값만 조회한다. 같은 트랜잭션에서 뒤이어 재고를 잠그고 차감할 때
    // 미리 읽어 둔 엔티티가 있으면 잠금 조회가 그 엔티티의 이전 재고 값으로 계산하기 때문이다
    public Map<Long, Long> findMartIdsOf(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findMartIdsByIdIn(productIds).stream()
            .collect(Collectors.toMap(ProductMartId::getProductId, ProductMartId::getMartId));
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
}
