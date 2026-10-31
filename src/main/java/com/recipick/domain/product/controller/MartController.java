package com.recipick.domain.product.controller;


import com.recipick.domain.product.dto.MartMapResponse;
import com.recipick.domain.product.service.MartService;
import com.recipick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/config/marts")
public class MartController {

    private final MartService martService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MartMapResponse>>> getMarts(){
        return ResponseEntity.ok(ApiResponse.success(martService.findOpenMartsForMap()));
    }
}
