package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.response.MemberAddressListResponse;
import com.recipick.domain.member.service.MemberAddressService;
import com.recipick.domain.member.service.MemberService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAddressFacade {

    private static final int MAX_COUNT = 5;

    private final MemberService memberService;
    private final MemberAddressService memberAddressService;
    private final ServiceAreaFacade serviceAreaFacade;

    public MemberAddressListResponse findAddresses(Long memberId) {
        Long selectedAddressId = memberService.getSelectedAddressId(memberId).orElse(null);
        // 같은 법정동의 주소는 이번 조회 안에서만 판정 결과를 공유한다.
        Map<String, Boolean> availability = new HashMap<>();
        List<MemberAddressListResponse.Address> addresses = memberAddressService
                .findAddresses(memberId, selectedAddressId).stream()
                .map(address -> new MemberAddressListResponse.Address(address.addressId(), address.label(),
                        address.address(), address.detailAddress(), address.legalDongCode(),
                        availability.computeIfAbsent(address.legalDongCode(),
                                code -> serviceAreaFacade.findAvailableMart(code).isPresent()),
                        address.addressId().equals(selectedAddressId)))
                .toList();
        return new MemberAddressListResponse(MAX_COUNT, addresses);
    }
}
