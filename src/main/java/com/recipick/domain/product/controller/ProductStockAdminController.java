package com.recipick.domain.product.controller;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.StockAdjustmentHistoryResponse;
import com.recipick.domain.product.dto.StockAdjustmentRequest;
import com.recipick.domain.product.dto.StockAdjustmentResponse;
import com.recipick.domain.product.service.ProductStockService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products/{productId}/stock-adjustments")
public class ProductStockAdminController {

    private final ProductStockService productStockService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockAdjustmentResponse>> adjustStock(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId,
        @Valid @RequestBody StockAdjustmentRequest request
        ){
        StockAdjustmentResponse response = productStockService.adjustStock(
            AdminRole.valueOf(principal.role()), principal.martId(), principal.id(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("재고가 조정되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StockAdjustmentHistoryResponse>>> getStockAdjustmentHistory(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId,
        Pageable pageable
    ){
        PageResponse<StockAdjustmentHistoryResponse> response = productStockService.getStockAdjustments(
            AdminRole.valueOf(principal.role()), principal.martId(), productId, pageable);
            return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", response));
    }
}
