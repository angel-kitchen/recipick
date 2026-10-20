package com.recipick.domain.product.service;

import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;



import static org.awaitility.Awaitility.given;

@ExtendWith(MockitoExtension.class)
public class MartServiceTest {

    @Mock
    private MartRepository martRepository;

    @InjectMocks
    private MartService martService;

    private MartCreateRequest request(String latitude, String longitude,
                                      Integer radius) {
        return new MartCreateRequest(
            31L, "정자동 마트", new BigDecimal(latitude),
            new BigDecimal(longitude), radius);
    }

    private void stubSave() {
        given(martRepository.save(any(Mart.class))).willAnswer(invocation -> {
            Mart mart = invocation.getArgument(0);
            ReflectionTestUtils.setField(mart, "id", 1L);
            return mart;
        });
    }

    @Test
    @DisplayName("등록 : 성공하면 저장된 martId를 돌려주고 상태는 OPEN이다")
    void createMart() {
        given(martRepository.existsByRegionId(31L)).willReturn(false);
        stubSave();

        MartIdResponse result = martService.createMart(request("37.367000", "127.1080000", 2500));

        assertThat(result.martId()).isEqualTo(1L);
        ArgumentCaptor<Mart> captor = ArgumentCaptor.forClass(Mart.class);
        verify(martRepository).save(captor.capture());
        assertThat(captor.getValue().getServiceRadiusM()).isEqualTo(2500);
        assertThat(captor.getValue().getStatus().name()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("등록 : 반경을 생략하면 기본값 2000이 저장된다.")
    void CreateMart_defaultRadius() {
        given(martRepository.existsByRegionId(31L)).willReturn(false);
        stubSave();

        martService.createMart(request("37.367000", "127.1080000", null));

        ArgumentCaptor<Mart> captor = ArgumentCaptor.forClass(Mart.class);
        verify(martRepository).save(captor.capture());
        assertThat(captor.getValue().getServiceRadiusM()).isEqualTo(2000);
    }

    @Test
    @DisplayName("등록 : 위도, 경도가 경계값(90,180)이면 성공한다.")
    void createMart_boundaryCoordinates() {
        given(martRepository.existsByRegionId(31L)).willReturn(false);
        stubSave();

        MartIdResponse result = martService.createMart(request("90", "-180", 1));

        assertThat(result.martId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("등록: 위도가 범위 밖이면 INVALID_MART_AREA")
    void createMart_invalidLatitude() {
        assertThatThrownBy(() -> martService.createMart(request("90.0000001",
            "127.1080000", 2000)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        verify(martRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 반경이 0 이하이면 INVALID_MART_AREA")
    void createMart_invalidRadius() {
        assertThatThrownBy(() -> martService.createMart(request("37.3670000",
            "127.1080000", 0)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        assertThatThrownBy(() -> martService.createMart(request("37.3670000",
            "127.1080000", -1)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        verify(martRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 : 그 지역에 이미 마트가 있으면 MART_REGION_DUPLICATED")
    void createMart_regionDuplicated() {
        given(martRepository.existsByRegionId(31L)).willReturn(true);

        assertThatThrownBy(() -> martService.createMart(request("37.3670000",
            "127.1080000", 2000)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.MART_REGION_DUPLICATED);
        verify(martRepository, never()).save(any());
    }
}
