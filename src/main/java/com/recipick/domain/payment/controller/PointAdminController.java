package com.recipick.domain.payment.controller;

// 회원별 포인트 관리자 조회. 기존 회원 하위 관리자 API(/api/admin/members/{memberId}/memberships)와 같은 경로 모양
// /api/admin/**은 SecurityConfig에서 MART_ADMIN에게도 열려 있어 SUPER_ADMIN 전용을 클래스 단위로 막는다.
// 잘못된 ID는 조회·감사 기록 전에 입구에서 끊는다

import com.recipick.domain.payment.dto.response.PointBalanceResponse;
import com.recipick.domain.payment.dto.response.PointHistoryResponse;
import com.recipick.domain.payment.facade.PointAdminQueryFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/members/{memberId}/points")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PointAdminController {

    private final PointAdminQueryFacade pointAdminQueryFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getBalance(@Positive @PathVariable Long memberId) {
            return ResponseEntity.ok(ApiResponse.success("포인트를 조회했습니다.",
                pointAdminQueryFacade.getBalance(memberId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<PointHistoryResponse>>> findHistory(
        @Positive @PathVariable Long memberId,
        @Positive @RequestParam(required = false) Long orderId,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("포인트 이력을 조회했습니다.",
            pointAdminQueryFacade.findHistory(memberId, orderId, pageable)));
    }
}
