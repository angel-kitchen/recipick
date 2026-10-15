package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.MartProduct;
import com.recipick.domain.product.entity.MartProductId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MartProductRepository extends JpaRepository<MartProduct, MartProductId> {
}
