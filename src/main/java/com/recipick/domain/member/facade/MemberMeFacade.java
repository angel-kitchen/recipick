package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.MemberAddressData;
import com.recipick.domain.member.dto.MemberProfile;
import com.recipick.domain.member.dto.response.MemberMeResponse;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.product.dto.OpenMartData;
import com.recipick.domain.product.dto.response.MartResponse;
import com.recipick.domain.product.entity.MartStatus;
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
        Optional<OpenMartData> availableMart = address == null ? Optional.empty()
                : serviceAreaFacade.findAvailableMart(address.legalDongCode());
        Optional<MartResponse> assigned = profile.martId() == null ? Optional.empty() : martService.findMart(profile.martId());
        MemberMeResponse.Mart mart = assigned
                .map(value -> new MemberMeResponse.Mart(value.martId(), value.name())).orElse(null);
        boolean serviceAvailable = mart != null
                && availableMart.filter(value -> value.martId().equals(profile.martId())).isPresent();
        MemberMeResponse.CurrentAddress currentAddress = address == null ? null : new MemberMeResponse.CurrentAddress(
                address.addressId(), address.label(), address.address(), address.detailAddress(),
                address.legalDongCode(), serviceAvailable, serviceAvailable ? null
                        : assigned.filter(value -> value.status() != MartStatus.OPEN).isPresent() ? "MART_CLOSED"
                        : availableMart.isPresent() ? "MART_MISMATCH" : "OUTSIDE_SERVICE_AREA");
        return new MemberMeResponse(profile.memberId(), profile.email(), profile.name(), profile.phone(),
                profile.locationAgreed(), mart, currentAddress,
                membershipService.getSummary(memberId, LocalDateTime.now(clock)),
                membershipService.isTrialEligible(memberId));
    }
}
