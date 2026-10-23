package com.recipick.domain.product.facade;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.ProductCreateRequest;
import com.recipick.domain.product.dto.ProductIdResponse;
import com.recipick.domain.product.dto.ProductUpdateRequest;
import com.recipick.domain.product.service.ProductService;
import com.recipick.domain.recipe.service.IngredientService;
import com.recipick.global.common.SaleUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProductAdminFacade {

    private final IngredientService ingredientService;
    private final ProductService productService;

    @Transactional
    public ProductIdResponse createProduct(AdminRole role, Long adminMartId, ProductCreateRequest request){
        SaleUnit ingredientUnit = request.ingredientId() == null
            ? null
            : ingredientService.getIngredient(request.ingredientId()).getUnit();
        return productService.createProduct(role, adminMartId, request, ingredientUnit);
    }

    @Transactional
    public ProductIdResponse updateProduct(AdminRole role, Long adminMartId, Long productId,
                                           ProductUpdateRequest request){
        SaleUnit ingredientUnit = request.ingredientId() == null
            ? null
            : ingredientService.getIngredient(request.ingredientId()).getUnit();
        return productService.updateProduct(role, adminMartId, productId, request, ingredientUnit);
    }
}
