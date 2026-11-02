package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.request.MembershipPaymentAttemptSearchRequest;
import com.recipick.domain.payment.dto.response.MembershipPaymentAttemptDetailResponse;
import com.recipick.domain.payment.dto.response.MembershipPaymentAttemptSummaryResponse;
import com.recipick.domain.payment.dto.response.MembershipPaymentRecheckResponse;
import com.recipick.domain.payment.facade.MembershipPaymentAdminFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/**은 SecurityConfig에서 MART_ADMIN에게도 열려 있다. 멤버십 결제 운영 조회·재조회는 SUPER_ADMIN 전용이라 클래스 단위로 막는다
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/membership-payment-attempts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MembershipPaymentAdminController {

    private final MembershipPaymentAdminFacade membershipPaymentAdminFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MembershipPaymentAttemptSummaryResponse>>> findAttempts(
        @Valid @ModelAttribute MembershipPaymentAttemptSearchRequest request
    ) {
        PageResponse<MembershipPaymentAttemptSummaryResponse> response =
            membershipPaymentAdminFacade.findAttempts(request);
        return ResponseEntity.ok(ApiResponse.success("멤버십 결제 시도를 조회했습니다.", response));
    }

    @GetMapping("/{attemptId}")
    public ResponseEntity<ApiResponse<MembershipPaymentAttemptDetailResponse>> getAttempt(
        @PathVariable Long attemptId
    ) {
        MembershipPaymentAttemptDetailResponse response = membershipPaymentAdminFacade.getAttempt(attemptId);
        return ResponseEntity.ok(ApiResponse.success("멤버십 결제 시도 상세를 조회했습니다.", response));
    }

    @PostMapping("/{attemptId}/recheck")
    public ResponseEntity<ApiResponse<MembershipPaymentRecheckResponse>> recheck(
        @PathVariable Long attemptId
    ) {
        MembershipPaymentRecheckResponse response = membershipPaymentAdminFacade.recheck(attemptId);
        return ResponseEntity.ok(ApiResponse.success("PortOne 결제 상태를 다시 확인했습니다.", response));
    }
}
