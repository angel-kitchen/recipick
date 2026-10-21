package com.recipick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.repository.MartRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MartServiceFindMartTest {

    @Mock
    private MartRepository repository;
    @InjectMocks
    private MartService service;

    @Test
    @DisplayName("운영 중지된 지정 마트도 ID로 조회해 DTO로 반환한다")
    void findsClosedMart() {
        // given
        Mart mart = Mart.builder().regionId(1L).name("삼청 마트").latitude(BigDecimal.ZERO)
                .longitude(BigDecimal.ZERO).serviceRadiusM(2000).build();
        ReflectionTestUtils.setField(mart, "id", 7L);
        ReflectionTestUtils.setField(mart, "status", MartStatus.CLOSED);
        given(repository.findById(7L)).willReturn(Optional.of(mart));

        // when
        Optional<MartResponse> response = service.findMart(7L);

        // then
        assertThat(response).isPresent();
        assertThat(response.orElseThrow().martId()).isEqualTo(7L);
        assertThat(response.orElseThrow().status()).isEqualTo(MartStatus.CLOSED);
    }

    @Test
    @DisplayName("지정 마트가 존재하지 않으면 빈 결과를 반환한다")
    void returnsEmptyForMissingMart() {
        // given
        given(repository.findById(7L)).willReturn(Optional.empty());

        // when
        Optional<MartResponse> response = service.findMart(7L);

        // then
        assertThat(response).isEmpty();
    }
}
