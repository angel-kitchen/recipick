package com.recipick.domain.product.controller;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.dto.MartUpdateRequest;
import com.recipick.domain.product.dto.StockAdjustmentHistoryResponse;
import com.recipick.domain.product.facade.MartFacade;
import com.recipick.domain.product.service.MartService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.eclipse.jdt.internal.compiler.apt.dispatch.AptProblem;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/marts")
public class MartAdminController {

    private final MartFacade martFacade;
    private final MartService martService;

    @PostMapping
    public ResponseEntity<ApiResponse<MartIdResponse>> createMart(
        @Valid @RequestBody MartCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(martFacade.createMart(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MART_ADMIN')")
    public ResponseEntity<ApiResponse<List<MartResponse>>> getMarts(
        @AuthenticationPrincipal AuthPrincipal principal
    ) {
        List<MartResponse> response = martService.getMarts(
            AdminRole.valueOf(principal.role()), principal.martId());
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", response));
    }

    @PatchMapping("/{martId]")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MART_ADMIN')")
    public ResponseEntity<ApiResponse<MartIdResponse>> updateMart(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long martId,
        @Valid @RequestBody MartUpdateRequest request
    ) {
        MartIdResponse response = martService.updateMart(
            AdminRole.valueOf(principal.role()), principal.martId(), martId, request);
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", response));
    }
}
