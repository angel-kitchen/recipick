package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.response.ServiceAreaCheckResponse;
import com.recipick.domain.member.service.RegionService;
import com.recipick.domain.product.dto.OpenMartResponse;
import com.recipick.domain.product.service.MartService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceAreaFacade {

    private final RegionService regionService;
    private final MartService martService;

    public ServiceAreaCheckResponse checkServiceArea(String legalDongCode) {
        return findAvailableMart(legalDongCode)
                .map(mart -> new ServiceAreaCheckResponse(true, mart.name()))
                .orElseGet(() -> new ServiceAreaCheckResponse(false, null));
    }

    public Optional<OpenMartResponse> findAvailableMart(String legalDongCode) {
        return regionService.getRegionIdByLegalDongCode(legalDongCode)
                .flatMap(regionId -> martService.findOpenMarts(regionId).stream().findFirst());
    }
}
