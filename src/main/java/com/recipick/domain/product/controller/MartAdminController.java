package com.recipick.domain.product.controller;

import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.facade.MartFacade;
import com.recipick.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/marts")
public class MartAdminController {

    private final MartFacade martFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<MartIdResponse>> createMart(
        @Valid @RequestBody MartCreateRequest request){
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(martFacade.createMart(request)));
    }
}
