package com.recipick.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.dto.response.ServiceAreaCheckResponse;
import com.recipick.domain.member.service.RegionService;
import com.recipick.domain.product.dto.OpenMartResponse;
import com.recipick.domain.product.service.MartService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceAreaFacadeTest {

    @Mock
    private RegionService regionService;
    @Mock
    private MartService martService;
    @InjectMocks
    private ServiceAreaFacade facade;

    @Test
    @DisplayName("미등록 법정동은 마트를 조회하지 않고 이용 불가로 응답한다")
    void skipsMartLookupForUnsupportedRegion() {
        // given
        given(regionService.getRegionIdByLegalDongCode("9999999999")).willReturn(Optional.empty());

        // when
        ServiceAreaCheckResponse response = facade.checkServiceArea("9999999999");

        // then
        assertThat(response).isEqualTo(new ServiceAreaCheckResponse(false, null));
        verifyNoInteractions(martService);
    }

    @Test
    @DisplayName("지역은 등록되어 있어도 운영 중인 마트가 없으면 이용 불가다")
    void returnsUnavailableWithoutOpenMart() {
        // given
        given(regionService.getRegionIdByLegalDongCode("1111014000")).willReturn(Optional.of(12L));
        given(martService.findOpenMarts(12L)).willReturn(List.of());

        // when
        ServiceAreaCheckResponse response = facade.checkServiceArea("1111014000");

        // then
        assertThat(response).isEqualTo(new ServiceAreaCheckResponse(false, null));
        verify(martService).findOpenMarts(12L);
    }

    @Test
    @DisplayName("지역의 실제 ID로 조회한 운영 마트 목록의 첫 마트 이름을 반환한다")
    void returnsFirstOpenMart() {
        // given
        given(regionService.getRegionIdByLegalDongCode("1111014000")).willReturn(Optional.of(12L));
        given(martService.findOpenMarts(12L)).willReturn(List.of(
                new OpenMartResponse(42L, "첫 마트", 12L), new OpenMartResponse(43L, "다음 마트", 12L)));

        // when
        ServiceAreaCheckResponse response = facade.checkServiceArea("1111014000");

        // then
        assertThat(response).isEqualTo(new ServiceAreaCheckResponse(true, "첫 마트"));
        verify(martService).findOpenMarts(12L);
    }
}
