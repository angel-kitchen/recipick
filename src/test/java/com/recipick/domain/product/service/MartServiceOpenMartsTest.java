package com.recipick.domain.product.service;

import com.recipick.domain.product.dto.response.MartMapResponse;
import com.recipick.domain.product.dto.OpenMartData;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.repository.MartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.BDDMockito.given;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class MartServiceOpenMartsTest {

    @Mock
    private MartRepository martRepository;

    @Mock
    private com.recipick.global.audit.AdminAuditRecorder auditRecorder;

    @InjectMocks
    private MartService martService;

    private Mart mart(Long id, Long regionId, String name) {
        Mart mart = Mart.builder()
            .regionId(regionId)
            .name(name)
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(mart, "id", id);
        return mart;
    }

    @Test
    @DisplayName("전체: 운영 중 마트를 응답으로 바꿔 돌려준다")
    void findOpenMarts() {
        given(martRepository.findAllByStatusOrderByIdAsc(MartStatus.OPEN))
            .willReturn(List.of(mart(1L, 31L, "삼청동 마트"), mart(2L, 32L, "혜화동 마트")));

        List<OpenMartData> result = martService.findOpenMarts();

        assertThat(result).extracting(OpenMartData::martId).containsExactly(1L, 2L);
        assertThat(result).extracting(OpenMartData::name).containsExactly("삼청동 마트", "혜화동 마트");
    }

    @Test
    @DisplayName("지역 : 그 지역의 운영 중 마트만 돌려준다")
    void findOpenMarts_byRegion() {
        given(martRepository.findAllByRegionIdAndStatusOrderByIdAsc(31L, MartStatus.OPEN))
            .willReturn(List.of(mart(1L, 31L, "삼청동 마트")));

        List<OpenMartData> result = martService.findOpenMarts(31L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionId()).isEqualTo(31L);
    }

    @Test
    @DisplayName("지역: 마트가 없거나 모두 CLOSED면 예외 없이 빈 목록")
    void findOpenMarts_byRegion_empty() {
        given(martRepository.findAllByRegionIdAndStatusOrderByIdAsc(99L, MartStatus.OPEN))
            .willReturn(List.of());

        assertThat(martService.findOpenMarts(99L)).isEmpty();
    }

    @Test
    @DisplayName("지도용: 위도나 경도가 없는 마트는 제외하고 ID 순서를 유지한다")
    void findMartsForMap_excludesMartWithoutCoordinates() {
        Mart noLatitude = Mart.builder()
            .regionId(33L)
            .name("위도 없는 마트")
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(noLatitude, "id", 2L);

        Mart noLongitude = Mart.builder()
            .regionId(34L)
            .name("경도 없는 마트")
            .latitude(new BigDecimal("37.3670000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(noLongitude, "id", 3L);

        Mart planned = mart(4L, null, "녹번마트 (준비중)");
        planned.update(null, null, null, MartStatus.CLOSED);

        given(martRepository.findAllByOrderByIdAsc())
            .willReturn(List.of(mart(1L, 31L, "삼청동 마트"), noLatitude, noLongitude, planned));

        List<MartMapResponse> result = martService.findMartsForMap();

        assertThat(result).extracting(MartMapResponse::martId).containsExactly(1L, 4L);
        assertThat(result.get(1).status()).isEqualTo(MartStatus.CLOSED);
        assertThat(result.get(1).name()).isEqualTo("녹번마트 (준비중)");
    }
}
