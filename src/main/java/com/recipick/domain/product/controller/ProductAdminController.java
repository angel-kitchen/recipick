package com.recipick.domain.product.controller;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.response.AdminProductResponse;
import com.recipick.domain.product.dto.request.AdminProductSearchRequest;
import com.recipick.domain.product.dto.request.ProductCreateRequest;
import com.recipick.domain.product.dto.request.ProductDiscountBulkRequest;
import com.recipick.domain.product.dto.request.ProductDiscountUpdateRequest;
import com.recipick.domain.product.dto.response.ProductDiscountBulkResponse;
import com.recipick.domain.product.dto.response.ProductDiscountResponse;
import com.recipick.domain.product.dto.response.ProductIdResponse;
import com.recipick.domain.product.dto.response.ProductPriceResponse;
import com.recipick.domain.product.dto.request.ProductPriceUpdateRequest;
import com.recipick.domain.product.dto.response.ProductStatusResponse;
import com.recipick.domain.product.dto.request.ProductStatusUpdateRequest;
import com.recipick.domain.product.dto.request.ProductUpdateRequest;
import com.recipick.domain.product.facade.ProductAdminFacade;
import com.recipick.domain.product.service.ProductService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products")
public class ProductAdminController {

    private final ProductService productService;
    private final ProductAdminFacade productAdminFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminProductResponse>>> getProducts(
        @AuthenticationPrincipal AuthPrincipal principal,
        @ModelAttribute AdminProductSearchRequest request,
        Pageable pageable
        ) {
        PageResponse<AdminProductResponse> response = productService.getAdminProducts(
            AdminRole.valueOf(principal.role()), principal.martId(), request, pageable);
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductIdResponse>> createProduct(
        @AuthenticationPrincipal AuthPrincipal principal,
        @Valid @RequestBody ProductCreateRequest request
        ) {
        ProductIdResponse response = productAdminFacade.createProduct(
            AdminRole.valueOf(principal.role()), principal.martId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("등록되었습니다.", response));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductIdResponse>> updateProduct(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId,
        @Valid @RequestBody ProductUpdateRequest request
        ) {
        ProductIdResponse response = productAdminFacade.updateProduct(
            AdminRole.valueOf(principal.role()), principal.martId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", response));
    }

    @PatchMapping("/{productId}/price")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> updatePrice(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId,
        @Valid @RequestBody ProductPriceUpdateRequest request
        ) {
        ProductPriceResponse response = productService.updatePrice(
            AdminRole.valueOf(principal.role()), principal.martId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", response));
    }

    @PatchMapping("/discounts")
    public ResponseEntity<ApiResponse<ProductDiscountBulkResponse>> updateDiscounts(
        @AuthenticationPrincipal AuthPrincipal principal,
        @Valid @RequestBody ProductDiscountBulkRequest request
    ) {
        ProductDiscountBulkResponse response = productService.updateDiscountBulk(
            AdminRole.valueOf(principal.role()), principal.martId(), request);
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", response));
    }

    @PatchMapping("/{productId}/discount")
    public ResponseEntity<ApiResponse<ProductDiscountResponse>> updateDiscount(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId,
        @Valid @RequestBody ProductDiscountUpdateRequest request
    ) {
        ProductDiscountResponse response = productService.updateDiscount(
            AdminRole.valueOf(principal.role()), principal.martId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", response));
    }

    @DeleteMapping("/{productId}/discount")
    public ResponseEntity<ApiResponse<ProductDiscountResponse>> clearDiscount(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId
    ) {
        ProductDiscountResponse response = productService.clearDiscount(
            AdminRole.valueOf(principal.role()), principal.martId(), productId);
        return ResponseEntity.ok(ApiResponse.success("해제되었습니다.", response));
    }

    @PatchMapping("/{productId}/status")
    public ResponseEntity<ApiResponse<ProductStatusResponse>> changeStatus(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId,
        @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        ProductStatusResponse response = productService.changeStatus(
            AdminRole.valueOf(principal.role()), principal.martId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", response));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId
    ) {
        productService.deleteProduct(AdminRole.valueOf(principal.role()), principal.martId(), productId);
        return ResponseEntity.ok(ApiResponse.<Void>success("삭제되었습니다.", null));
    }
}
