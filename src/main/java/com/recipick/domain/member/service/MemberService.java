package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.MemberAddressData;
import com.recipick.domain.member.dto.MemberProfile;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    // 현재 배송지가 없거나 서비스 지역 밖이면 이용 마트가 없으므로 empty를 돌려준다.
    public Optional<Long> getMartId(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        return Optional.ofNullable(member.getMartId());
    }
    public MemberProfile getProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        MemberAddress address = member.getSelectedAddress();
        MemberAddressData currentAddress = address == null ? null : new MemberAddressData(
                address.getId(), address.getLabel(), address.getAddress(),
                address.getDetailAddress(), address.getLegalDongCode());
        return new MemberProfile(member.getId(), member.getEmail(), member.getName(), member.getPhone(),
                member.getLocationAgreedAt() != null, member.getMartId(), currentAddress);
    }

    public Optional<Long> getSelectedAddressId(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        return Optional.ofNullable(member.getSelectedAddress()).map(MemberAddress::getId);
    }

    @Transactional
    public Member getMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
