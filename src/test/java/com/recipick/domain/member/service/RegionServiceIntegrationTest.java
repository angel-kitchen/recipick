package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.repository.RegionRepository;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RegionServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RegionService regionService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("등록된 지역 ID는 존재하고 없는 지역 ID는 존재하지 않는다")
    void checksRegionExistence() {
        // given
        Long regionId = 1L;

        // when
        boolean existing = regionService.existsById(regionId);
        boolean missing = regionService.existsById(Long.MAX_VALUE);

        // then
        assertThat(existing).isTrue();
        assertThat(missing).isFalse();
    }

    @Test
    @DisplayName("법정동 코드로 실제 저장된 지역 ID를 조회한다")
    void getsRegionIdByLegalDongCode() {
        // given
        String legalDongCode = "4121010300";

        // when
        Optional<Long> regionId = regionService.getRegionIdByLegalDongCode(legalDongCode);

        // then
        assertThat(regionId).contains(2L);
    }

    @Test
    @DisplayName("서비스 지역 밖 법정동 코드는 오류 없이 빈 결과를 반환한다")
    void returnsEmptyForUnsupportedRegion() {
        // given
        String legalDongCode = "9999999999";

        // when
        Optional<Long> regionId = regionService.getRegionIdByLegalDongCode(legalDongCode);

        // then
        assertThat(regionId).isEmpty();
    }

    @Test
    @DisplayName("지역 초기 데이터를 반복 등록해도 기존 ID와 데이터는 유지된다")
    void seedsRegionsWithoutDuplicates() {
        // given
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("data.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        Long samcheongId = regionService.getRegionIdByLegalDongCode("1111014000").orElseThrow();
        Long haanId = regionService.getRegionIdByLegalDongCode("4121010300").orElseThrow();
        long count = regionRepository.count();

        // when
        populator.execute(dataSource);

        // then
        assertThat(regionService.getRegionIdByLegalDongCode("1111014000")).contains(samcheongId);
        assertThat(regionService.getRegionIdByLegalDongCode("4121010300")).contains(haanId);
        assertThat(samcheongId).isEqualTo(1L);
        assertThat(haanId).isEqualTo(2L);
        assertThat(regionRepository.count()).isEqualTo(count);
        assertThat(regionRepository.findById(samcheongId).orElseThrow().getName())
                .isEqualTo("서울특별시 종로구 삼청동");
        assertThat(regionRepository.findById(haanId).orElseThrow().getName()).isEqualTo("경기도 광명시 하안동");
    }
}
