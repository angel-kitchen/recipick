package com.recipick.domain.payment.controller;

import com.recipick.domain.payment.dto.request.BillingKeyRegisterRequest;
import com.recipick.domain.payment.dto.response.BillingKeyIssueRequestResponse;
import com.recipick.domain.payment.dto.response.BillingKeyMeResponse;
import com.recipick.domain.payment.dto.response.BillingKeyRegisterResponse;
import com.recipick.domain.payment.facade.BillingKeyRegistrationFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BillingKeyController {

    private final BillingKeyRegistrationFacade billingKeyRegistrationFacade;

    // 응답의 issueId는 일회성 토큰이라 브라우저·중간 캐시에 남기지 않는다
    @PostMapping("/api/billing-keys/issue-requests")
    public ResponseEntity<ApiResponse<BillingKeyIssueRequestResponse>> issue(@LoginMember Long memberId) {
        BillingKeyIssueRequestResponse response = billingKeyRegistrationFacade.issue(memberId);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResponse.success("카드 등록을 시작합니다.", response));
    }

    // 같은 키 재등록도 200이다(멱등). 결과 구분은 data.outcome
    @PostMapping("/api/billing-keys")
    public ResponseEntity<ApiResponse<BillingKeyRegisterResponse>> register(
        @LoginMember Long memberId,
        @Valid @RequestBody BillingKeyRegisterRequest request
    ) {
        BillingKeyRegisterResponse response = billingKeyRegistrationFacade.register(memberId, request);
        return ResponseEntity.ok(ApiResponse.success("카드가 등록되었습니다.", response));
    }

    @GetMapping("/api/billing-keys/me")
    public ResponseEntity<ApiResponse<BillingKeyMeResponse>> getMine(@LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(billingKeyRegistrationFacade.getMine(memberId)));
    }
}
