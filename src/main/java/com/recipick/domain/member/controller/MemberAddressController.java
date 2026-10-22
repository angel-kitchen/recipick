package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.request.MemberAddressCreateRequest;
import com.recipick.domain.member.dto.response.MemberAddressListResponse;
import com.recipick.domain.member.dto.response.MemberAddressResponse;
import com.recipick.domain.member.facade.MemberAddressFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    public ResponseEntity<ApiResponse<MemberAddressResponse>> createAddress(@LoginMember Long memberId,
            @Valid @RequestBody MemberAddressCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("배송지가 등록되었습니다.", memberAddressFacade.createAddress(memberId, request)));
    }

    @PutMapping("/{addressId}/select")
    public ResponseEntity<ApiResponse<MemberAddressResponse>> selectAddress(@LoginMember Long memberId,
            @PathVariable Long addressId) {
        return ResponseEntity.ok(ApiResponse.success("현재 배송지가 선택되었습니다.",
                memberAddressFacade.selectAddress(memberId, addressId)));
    }
}
