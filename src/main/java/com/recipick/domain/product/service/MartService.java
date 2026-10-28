package com.recipick.domain.product.service;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.dto.MartUpdateRequest;
import com.recipick.domain.product.dto.OpenMartResponse;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.global.common.PolicyConstants;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.recipick.global.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MartService {

    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private final MartRepository martRepository;

    @CacheEvict(cacheNames = CacheConfig.OPEN_MARTS, allEntries = true)
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

    @CacheEvict(cacheNames = CacheConfig.OPEN_MARTS, allEntries = true)
    @Transactional
    public MartIdResponse updateMart(AdminRole role, Long adminMartId, Long martId, MartUpdateRequest request){
        validateUpdatePermission(role, adminMartId, martId, request);

        Mart mart = martRepository.findByIdForUpdate(martId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.MART_NOT_FOUND));

        if((request.latitude() == null) != (request.longitude() == null)){
            throw new BusinessException(ProductErrorCode.INVALID_MART_AREA);
        }
        BigDecimal latitude = request.latitude() != null ? request.latitude() : mart.getLatitude();
        BigDecimal longitude = request.longitude() != null ? request.longitude() : mart.getLongitude();
        int radius = request.serviceRadiusM() != null ? request.serviceRadiusM() : mart.getServiceRadiusM();
        validateArea(latitude, longitude, radius);

        mart.update(request.latitude(), request.longitude(), request.serviceRadiusM(), request.status());
        return new MartIdResponse(mart.getId());
    }

    private void validateUpdatePermission(AdminRole role, Long adminMartId, Long martId, MartUpdateRequest request){
        if (role != AdminRole.MART_ADMIN){
            return;
        }
        if(!martId.equals(adminMartId)){
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }
        if(request.latitude() != null || request.longitude() != null || request.serviceRadiusM() != null){
            throw new BusinessException(AuthErrorCode.SUPER_ADMIN_ONLY);
        }
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

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.OPEN_MARTS, key = "'all'")
    public List<OpenMartResponse> findOpenMarts() {
        return martRepository.findAllByStatusOrderByIdAsc(MartStatus.OPEN).stream()
            .map(OpenMartResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.OPEN_MARTS, key = "'region:' + #regionId")
    public List<OpenMartResponse> findOpenMarts(Long regionId) {
        return martRepository.findAllByRegionIdAndStatusOrderByIdAsc(regionId, MartStatus.OPEN).stream()
            .map(OpenMartResponse::from)
            .toList();
    }
    @Transactional(readOnly = true)
    public Optional<MartResponse> findMart(Long martId) {
        return martRepository.findById(martId).map(MartResponse::from);
    }
}

