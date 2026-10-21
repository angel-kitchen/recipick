package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MartRepository extends JpaRepository<Mart, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mart m where m.id = :martId")
    Optional<Mart> findByIdForUpdate(@Param("martId") Long martId);

    boolean existsByRegionId(Long regionId);

    List<Mart> findAllByStatusOrderByIdAsc(MartStatus status);

    List<Mart> findAllByRegionIdAndStatusOrderByIdAsc(Long regionId, MartStatus status);
}
