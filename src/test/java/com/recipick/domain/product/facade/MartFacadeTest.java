package com.recipick.domain.product.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.RegionService;
import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.service.MartService;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class MartFacadeTest {

    @Mock
    private MartService martService;

    @Mock
    private RegionService regionService;

    @InjectMocks
    private MartFacade martFacade;

    private MartCreateRequest request(Long regionId){
        return new MartCreateRequest(regionId, "삼청동 마트",
            new BigDecimal("37.5860000"), new BigDecimal("126.9820000"), 2000);
    }

    @Test
    @DisplayName("등록: 지역이 있으면 마트를 등록한다")
    void createMart() {
        // given
        MartCreateRequest request = request(1L);
        given(regionService.existsById(1L)).willReturn(true);
        given(martService.createMart(request)).willReturn(new MartIdResponse(10L));
        // when
        MartIdResponse result = martFacade.createMart(request);

        //then
        assertThat(result.martId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("등록: 없는 지역이면 REGION_NOT_FOUND이고 마트 등록은 호출되지 않는다")
    void createMart_regionNotFound(){
        // given
        given(regionService.existsById(99L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> martFacade.createMart(request(99L)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(MemberErrorCode.REGION_NOT_FOUND);
        verify(martService, never()).createMart(any());
    }
}

