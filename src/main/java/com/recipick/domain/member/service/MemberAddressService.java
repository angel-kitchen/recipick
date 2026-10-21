package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.MemberAddressData;
import com.recipick.domain.member.repository.MemberAddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAddressService {

    private final MemberAddressRepository memberAddressRepository;

    public List<MemberAddressData> findAddresses(Long memberId, Long selectedAddressId) {
        return memberAddressRepository.findMemberAddresses(memberId, selectedAddressId).stream()
                .map(address -> new MemberAddressData(address.getId(), address.getLabel(),
                        address.getAddress(), address.getDetailAddress(), address.getLegalDongCode()))
                .toList();
    }
}
