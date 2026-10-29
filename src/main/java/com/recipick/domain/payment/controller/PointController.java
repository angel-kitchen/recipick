package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.response.PointBalanceResponse;
import com.recipick.domain.payment.dto.response.PointEarningResponse;
import com.recipick.domain.payment.dto.response.PointHistoryResponse;
import com.recipick.domain.payment.service.PointQueryService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 회원 본인의 포인트 조회. 회원 ID는 토큰에서만 꺼낸다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointController {

    private final PointQueryService pointQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getBalance(@LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.success("포인트를 조회했습니다.", pointQueryService.getBalance(memberId)));
    }

    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<PageResponse<PointEarningResponse>>> findEarnings(
        @LoginMember Long memberId,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("적립 내역을 조회했습니다.",
            pointQueryService.findEarnings(memberId, pageable)));
    }

    // 주문 ID가 숫자가 아니면 COMMON-002, 0 이하면 COMMON-001로 조회 전에 끊는다
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<PointHistoryResponse>>> findHistory(
        @LoginMember Long memberId,
        @Positive @RequestParam(required = false) Long orderId,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("포인트 이력을 조회했습니다.",
            pointQueryService.findHistory(memberId, orderId, pageable)));
    }
}
