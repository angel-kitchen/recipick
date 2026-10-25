package com.recipick.domain.product.controller;

import com.recipick.domain.product.dto.ProductDetailResponse;
import com.recipick.domain.product.dto.ProductSearchRequest;
import com.recipick.domain.product.dto.ProductSearchResponse;
import com.recipick.domain.product.facade.ProductFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductFacade productFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSearchResponse>>> searchProducts(
        @LoginMember Long memberId,
        @Valid @ModelAttribute ProductSearchRequest request,
        Pageable pageable
        ){
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.",
            productFacade.searchProducts(memberId, request, pageable)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(
        @LoginMember Long memberId,
        @PathVariable Long productId
    ){
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.",
            productFacade.getProduct(memberId, productId)));
    }
}
