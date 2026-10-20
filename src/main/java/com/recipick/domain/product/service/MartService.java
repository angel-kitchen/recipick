package com.recipick.domain.product.service;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.global.common.PolicyConstants;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MartService {

    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private final MartRepository martRepository;

    @Transactional
    public MartIdResponse createMart(MartCreateRequest request){
        int radius = request.serviceRadiusM() != null
            ? request.serviceRadiusM()
            : PolicyConstants.MART_SERVICE_RADIUS_METERS;

        validateArea(request.latitude(), request.longitude(), radius);

        if(martRepository.existsByRegionId(request.regionId())){
            throw new BusinessException(ProductErrorCode.MART_REGION_DUPLICATED);
        }

        Mart mart = Mart.builder()
            .regionId(request.regionId())
            .name(request.name())
            .latitude(request.latitude())
            .longitude(request.longitude())
            .serviceRadiusM(radius)
            .build();
        return new MartIdResponse(martRepository.save(mart).getId());
    }

    private void validateArea(BigDecimal latitude, BigDecimal longitude, int radius){
        if(latitude.abs().compareTo(MAX_LATITUDE)>0
            || longitude.abs().compareTo(MAX_LONGITUDE)>0
            || radius <= 0){
            throw new BusinessException(ProductErrorCode.INVALID_MART_AREA);
        }
    }

    @Transactional(readOnly = true)
    public List<MartResponse> getMarts(AdminRole role, Long adminMartId){
        if(role == AdminRole.SUPER_ADMIN){
            return martRepository.findAll().stream()
                .map(MartResponse::from)
                .toList();
        }
        return martRepository.findById(adminMartId).stream()
            .map(MartResponse::from)
            .toList();
    }
}

