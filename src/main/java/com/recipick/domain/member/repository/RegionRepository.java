package com.recipick.domain.member.repository;

import com.recipick.domain.member.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {
}
