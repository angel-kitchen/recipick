package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MartRepository extends JpaRepository<Mart, Long> {

    boolean existsByRegionId(Long regionId);

    List<Mart> findAllByStatusOrderByIdAsc(MartStatus status);

    List<Mart> findAllByRegionIdAndStatusOrderByIdAsc(Long regionId, MartStatus status);
}
