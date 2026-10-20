package com.recipick.domain.product.facade;

import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.service.MartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MartFacade {

    private final MartService martService;

    @Transactional
    public MartIdResponse createMart(MartCreateRequest request){
        // RegionService의 지역 존재 확인 메서드 완성 후 여기서 먼저 호출(REGION_NOT_FOUND)
        return martService.createMart(request);
    }
}
