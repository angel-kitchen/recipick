package com.recipick.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.dto.MemberAddressData;
import com.recipick.domain.member.dto.response.MemberAddressListResponse;
import com.recipick.domain.member.service.MemberAddressService;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.product.dto.OpenMartResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberAddressFacadeTest {

    @Mock
    private MemberService memberService;
    @Mock
    private MemberAddressService addressService;
    @Mock
    private ServiceAreaFacade serviceAreaFacade;
    @InjectMocks
    private MemberAddressFacade facade;

    @Test
    @DisplayName("같은 법정동은 요청마다 한 번만 판정하고 다음 요청에서는 새로 판정한다")
    void reusesOnlyWithinRequest() {
        // given
        given(memberService.getSelectedAddressId(1L)).willReturn(Optional.of(10L));
        given(addressService.findAddresses(1L, 10L)).willReturn(List.of(
                new MemberAddressData(10L, "집", "주소", "1호", "1111014000"),
                new MemberAddressData(11L, "회사", "주소", "2호", "1111014000")));
        given(serviceAreaFacade.findAvailableMart("1111014000"))
                .willReturn(Optional.of(new OpenMartResponse(1L, "삼청 마트", 1L)))
                .willReturn(Optional.empty());

        // when
        MemberAddressListResponse first = facade.findAddresses(1L);
        MemberAddressListResponse second = facade.findAddresses(1L);

        // then
        assertThat(first.addresses()).allMatch(MemberAddressListResponse.Address::serviceAvailable);
        assertThat(second.addresses()).noneMatch(MemberAddressListResponse.Address::serviceAvailable);
        assertThat(first.addresses().get(0).selected()).isTrue();
        assertThat(first.addresses().get(1).selected()).isFalse();
        verify(serviceAreaFacade, times(2)).findAvailableMart("1111014000");
        assertThat(first.toString()).doesNotContain("1호");
        assertThat(first.addresses().get(0).toString()).doesNotContain("1호");
    }

    @Test
    @DisplayName("배송지가 없으면 서비스 지역을 조회하지 않는다")
    void skipsRegionLookupForEmptyAddresses() {
        // given
        given(memberService.getSelectedAddressId(1L)).willReturn(Optional.empty());
        given(addressService.findAddresses(1L, null)).willReturn(List.of());

        // when
        MemberAddressListResponse response = facade.findAddresses(1L);

        // then
        assertThat(response.maxCount()).isEqualTo(5);
        assertThat(response.addresses()).isEmpty();
        verifyNoInteractions(serviceAreaFacade);
    }
}
