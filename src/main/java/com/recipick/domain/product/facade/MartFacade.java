package com.recipick.domain.product.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.RegionService;
import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.service.MartService;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MartFacade {

    private final MartService martService;
    private final RegionService regionService;

    @Transactional
    public MartIdResponse createMart(MartCreateRequest request){
        if (!regionService.existsById(request.regionId())){
            throw new BusinessException(MemberErrorCode.REGION_NOT_FOUND);
        }
        return martService.createMart(request);
    }
}
