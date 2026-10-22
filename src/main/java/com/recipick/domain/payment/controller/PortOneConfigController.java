package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.response.PortOneConfigResponse;
import com.recipick.domain.payment.service.PortOneConfigService;
import com.recipick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PortOneConfigController {

    private final PortOneConfigService portOneConfigService;

    @GetMapping("/api/config/portone")
    public ResponseEntity<ApiResponse<PortOneConfigResponse>> getPublicConfig() {
        return ResponseEntity.ok(
            ApiResponse.success(portOneConfigService.getPublicConfig())
        );
    }
}
