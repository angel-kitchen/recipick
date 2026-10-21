package com.recipick.domain.product.repository;

import com.recipick.domain.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

    @Query("""
            SELECT p.ingredientId AS ingredientId, COUNT(p) AS productCount
            FROM Product p
            WHERE p.ingredientId IN :ingredientIds
              AND p.deleted = false
            GROUP BY p.ingredientId
            """)
    List<IngredientProductCount> countByIngredientIds(@Param("ingredientIds") Collection<Long> ingredientIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
