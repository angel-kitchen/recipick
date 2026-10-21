package com.recipick.domain.product.service;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.dto.AdminProductResponse;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
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
