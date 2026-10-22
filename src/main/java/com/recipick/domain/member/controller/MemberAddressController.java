package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.response.MemberAddressListResponse;
import com.recipick.domain.member.facade.MemberAddressFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/addresses")
@RequiredArgsConstructor
public class MemberAddressController {

    private final MemberAddressFacade memberAddressFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<MemberAddressListResponse>> findAddresses(@LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", memberAddressFacade.findAddresses(memberId)));
    }
}
