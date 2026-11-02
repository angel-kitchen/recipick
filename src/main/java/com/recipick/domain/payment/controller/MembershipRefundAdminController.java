package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.response.MembershipRefundRecheckResponse;
import com.recipick.domain.payment.dto.response.MembershipRefundRetryResponse;
import com.recipick.domain.payment.facade.MembershipRefundAdminFacade;
import com.recipick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/**은 SecurityConfig에서 MART_ADMIN에게도 열려 있다. 멤버십 환불 운영 재조회·재실행은 SUPER_ADMIN 전용이라 클래스 단위로 막는다
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/membership-refunds")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MembershipRefundAdminController {

    private final MembershipRefundAdminFacade membershipRefundAdminFacade;

    @PostMapping("/{refundId}/recheck")
    public ResponseEntity<ApiResponse<MembershipRefundRecheckResponse>> recheck(@PathVariable Long refundId) {
        MembershipRefundRecheckResponse response = membershipRefundAdminFacade.recheck(refundId);
        return ResponseEntity.ok(ApiResponse.success("PortOne 환불 상태를 다시 확인했습니다.", response));
    }

    @PostMapping("/{refundId}/retry")
    public ResponseEntity<ApiResponse<MembershipRefundRetryResponse>> retry(@PathVariable Long refundId) {
        MembershipRefundRetryResponse response = membershipRefundAdminFacade.retry(refundId);
        return ResponseEntity.ok(ApiResponse.success("환불 재실행을 접수했습니다.", response));
    }
}
