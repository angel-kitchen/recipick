package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.request.MemberAddressCreateRequest;
import com.recipick.domain.member.dto.request.MemberAddressUpdateRequest;
import com.recipick.domain.member.dto.response.MemberAddressListResponse;
import com.recipick.domain.member.dto.response.MemberAddressResponse;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.exception.AddressErrorCode;
import com.recipick.domain.member.service.MemberAddressService;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.product.dto.OpenMartResponse;
import com.recipick.domain.product.service.MartService;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAddressFacade {

    private final MemberService memberService;
    private final MemberAddressService memberAddressService;
    private final ServiceAreaFacade serviceAreaFacade;
    private final MartService martService;
    private final Clock clock;

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
        return new MemberAddressListResponse(MemberAddressService.MAX_COUNT, addresses);
    }

    @Transactional
    public MemberAddressResponse createAddress(Long memberId, MemberAddressCreateRequest request) {
        // 개수 조회보다 먼저 잠가 대기 중 시작된 이전 스냅샷으로 한도를 판단하지 않게 한다.
        Member member = memberService.getMemberForUpdate(memberId);
        long count = memberAddressService.countAddresses(memberId);
        if (count >= MemberAddressService.MAX_COUNT) {
            throw new BusinessException(AddressErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
        Long previousMartId = member.getMartId();
        Optional<OpenMartResponse> availableMart = serviceAreaFacade.findAvailableMart(request.legalDongCode());
        MemberAddress address = memberAddressService.createAddress(member, request);
        boolean selected = count == 0 || Boolean.TRUE.equals(request.select());
        if (selected) {
            member.selectAddress(address, availableMart.map(OpenMartResponse::martId).orElse(null));
        }
        if (Boolean.TRUE.equals(request.locationAgreed())) {
            member.agreeToLocation(LocalDateTime.now(clock));
        }
        MemberAddressResponse.Mart currentMart = member.getMartId() == null ? null
                : martService.findMart(member.getMartId())
                        .map(mart -> new MemberAddressResponse.Mart(mart.martId(), mart.name())).orElse(null);
        return new MemberAddressResponse(address.getId(), address.getLabel(), address.getAddress(),
                address.getDetailAddress(), address.getLegalDongCode(), availableMart.isPresent(), selected,
                currentMart, !Objects.equals(previousMartId, member.getMartId()));
    }

    @Transactional
    public MemberAddressResponse updateAddress(Long memberId, Long addressId, MemberAddressUpdateRequest request) {
        // 등록과 같은 회원 행을 먼저 잠가 선택·삭제 요청과의 경합을 직렬화한다.
        Member member = memberService.getMemberForUpdate(memberId);
        MemberAddress address = memberAddressService.getOwnedAddress(memberId, addressId);
        Long previousMartId = member.getMartId();
        if (request.label() != null) {
            address.rename(request.label().isBlank() ? null : request.label());
        }
        if (request.address() != null) {
            address.changeAddress(request.address(), request.detailAddress(), request.legalDongCode());
        }
        Optional<OpenMartResponse> availableMart = serviceAreaFacade.findAvailableMart(address.getLegalDongCode());
        boolean selected = member.getSelectedAddress() != null
                && address.getId().equals(member.getSelectedAddress().getId());
        if (selected) {
            member.selectAddress(address, availableMart.map(OpenMartResponse::martId).orElse(null));
        }
        MemberAddressResponse.Mart currentMart = member.getMartId() == null ? null
                : martService.findMart(member.getMartId())
                        .map(mart -> new MemberAddressResponse.Mart(mart.martId(), mart.name())).orElse(null);
        return new MemberAddressResponse(address.getId(), address.getLabel(), address.getAddress(),
                address.getDetailAddress(), address.getLegalDongCode(), availableMart.isPresent(), selected,
                currentMart, !Objects.equals(previousMartId, member.getMartId()));
    }
}
