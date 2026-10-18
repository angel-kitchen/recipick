package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Mart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MartRepository extends JpaRepository<Mart, Long> {

    boolean existsByRegionId(Long regionId);
}
