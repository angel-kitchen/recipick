package com.recipick.domain.product.service;

import com.recipick.domain.product.dto.OpenMartResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class MartServiceOpenMartsTest {

    @Mock
    private MartRepository martRepository;

    @InjectMocks
    private MartService martService;

    private Mart mart(Long id, Long regionId, String name){
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
    void findOpenMarts(){
        given(martRepository.findAllByStatusOrderByIdAsc(MartStatus.OPEN))
            .willReturn(List.of(mart(1L,31L,"삼청동 마트"), mart(2L,32L, "혜화동 마트")));

        List<OpenMartResponse> result = martService.findOpenMarts();

        assertThat(result).extracting(OpenMartResponse::martId).containsExactly(1L,2L);
        assertThat(result).extracting(OpenMartResponse::name).containsExactly("삼청동 마트", "혜화동 마트");
    }

    @Test
    @DisplayName("지역 : 그 지역의 운영 중 마트만 돌려준다")
    void findOpenMarts_byRegion(){
        given(martRepository.findAllByRegionIdAndStatusOrderByIdAsc(31L, MartStatus.OPEN))
            .willReturn(List.of(mart(1L, 31L, "삼청동 마트")));

        List<OpenMartResponse> result = martService.findOpenMarts(31L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionId()).isEqualTo(31L);
    }

    @Test
    @DisplayName("지역: 마트가 없거나 모두 CLOSED면 예외 없이 빈 목록")
    void findOpenMarts_byRegion_empty(){
        given(martRepository.findAllByRegionIdAndStatusOrderByIdAsc(99L, MartStatus.OPEN))
            .willReturn(List.of());

        assertThat(martService.findOpenMarts(99L)).isEmpty();
    }
}
