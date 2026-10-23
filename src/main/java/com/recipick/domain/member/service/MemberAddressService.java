package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.MemberAddressData;
import com.recipick.domain.member.dto.request.MemberAddressCreateRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.exception.AddressErrorCode;
import com.recipick.domain.member.repository.MemberAddressRepository;
import com.recipick.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAddressService {

    public static final int MAX_COUNT = 5;

    private final MemberAddressRepository memberAddressRepository;

    public List<MemberAddressData> findAddresses(Long memberId, Long selectedAddressId) {
        return memberAddressRepository.findMemberAddresses(memberId, selectedAddressId).stream()
                .map(address -> new MemberAddressData(address.getId(), address.getLabel(),
                        address.getAddress(), address.getDetailAddress(), address.getLegalDongCode()))
                .toList();
    }

    public MemberAddress getOwnedAddress(Long memberId, Long addressId) {
        return memberAddressRepository.findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new BusinessException(AddressErrorCode.ADDRESS_NOT_FOUND));
    }

    public long countAddresses(Long memberId) {
        return memberAddressRepository.countByMemberId(memberId);
    }

    @Transactional
    public MemberAddress createAddress(Member member, MemberAddressCreateRequest request) {
        return memberAddressRepository.save(MemberAddress.builder().member(member).label(request.label())
                .address(request.address()).detailAddress(request.detailAddress())
                .legalDongCode(request.legalDongCode()).build());
    }

    @Transactional
    public void deleteAddress(MemberAddress address) {
        memberAddressRepository.delete(address);
    }
}
