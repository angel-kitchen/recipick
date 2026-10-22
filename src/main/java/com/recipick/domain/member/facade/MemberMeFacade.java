package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.MemberAddressData;
import com.recipick.domain.member.dto.MemberProfile;
import com.recipick.domain.member.dto.response.MemberMeResponse;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.product.dto.OpenMartResponse;
import com.recipick.domain.product.service.MartService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberMeFacade {

    private final MemberService memberService;
    private final MembershipService membershipService;
    private final ServiceAreaFacade serviceAreaFacade;
    private final Clock clock;
    private final MartService martService;

    public MemberMeResponse getMe(Long memberId) {
        MemberProfile profile = memberService.getProfile(memberId);
        MemberAddressData address = profile.address();
        Optional<OpenMartResponse> availableMart = address == null ? Optional.empty()
                : serviceAreaFacade.findAvailableMart(address.legalDongCode());
        MemberMeResponse.Mart mart = profile.martId() == null ? null : martService.findMart(profile.martId())
                .map(value -> new MemberMeResponse.Mart(value.martId(), value.name())).orElse(null);
        boolean serviceAvailable = mart != null
                && availableMart.filter(value -> value.martId().equals(profile.martId())).isPresent();
        MemberMeResponse.CurrentAddress currentAddress = address == null ? null : new MemberMeResponse.CurrentAddress(
                address.addressId(), address.label(), address.address(), address.detailAddress(),
                address.legalDongCode(), serviceAvailable);
        return new MemberMeResponse(profile.memberId(), profile.email(), profile.name(), profile.phone(),
                profile.locationAgreed(), mart, currentAddress,
                membershipService.getSummary(memberId, LocalDateTime.now(clock)),
                membershipService.isTrialEligible(memberId));
    }
}
