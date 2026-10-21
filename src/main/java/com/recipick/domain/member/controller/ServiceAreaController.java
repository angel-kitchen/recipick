package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.request.ServiceAreaCheckRequest;
import com.recipick.domain.member.dto.response.ServiceAreaCheckResponse;
import com.recipick.domain.member.facade.ServiceAreaFacade;
import com.recipick.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-area")
@RequiredArgsConstructor
public class ServiceAreaController {

    private final ServiceAreaFacade serviceAreaFacade;

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<ServiceAreaCheckResponse>> checkServiceArea(
            @Valid @RequestBody ServiceAreaCheckRequest request) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.",
                serviceAreaFacade.checkServiceArea(request.legalDongCode())));
    }
}
