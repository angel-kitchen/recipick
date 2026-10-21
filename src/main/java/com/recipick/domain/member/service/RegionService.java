package com.recipick.domain.member.service;

import com.recipick.domain.member.entity.Region;
import com.recipick.domain.member.repository.RegionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;

    public boolean existsById(Long regionId) {
        return regionRepository.existsById(regionId);
    }

    public Optional<Long> getRegionIdByLegalDongCode(String legalDongCode) {
        return regionRepository.findByLegalDongCode(legalDongCode)
                .map(Region::getId);
    }
}
