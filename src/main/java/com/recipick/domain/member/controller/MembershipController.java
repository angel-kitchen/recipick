package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.response.MembershipCancelReservationResponse;
import com.recipick.domain.member.dto.response.MembershipMeResponse;
import com.recipick.domain.member.dto.request.MembershipTrialStartRequest;
import com.recipick.domain.member.dto.response.MembershipTrialStartResponse;
import com.recipick.domain.member.facade.MembershipTrialFacade;
import com.recipick.domain.member.service.MembershipCancelReservationService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;
    private final MembershipTrialFacade trialFacade;
    private final MembershipCancelReservationService cancelReservationService;

    @PostMapping("/trial")
    public ResponseEntity<ApiResponse<MembershipTrialStartResponse>> startTrial(
            @LoginMember Long memberId, @Valid @RequestBody MembershipTrialStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("무료체험이 시작되었습니다.", trialFacade.start(memberId, request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MembershipMeResponse>> getMe(@LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", membershipService.getMe(memberId)));
    }

    @PostMapping("/{membershipId}/cancel-reservation")
    public ResponseEntity<ApiResponse<MembershipCancelReservationResponse>> reserveCancellation(
            @LoginMember Long memberId, @Positive @PathVariable Long membershipId) {
        return ResponseEntity.ok(ApiResponse.success("해지 예약되었습니다.",
                cancelReservationService.reserve(memberId, membershipId)));
    }

}
