package com.recipick.domain.product.service;

import com.recipick.domain.product.repository.IngredientProductCount;
import com.recipick.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductIngredientService {

    private final ProductRepository productRepository;

    public Map<Long, Long> countLinkedProducts(Collection<Long> ingredientIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (ingredientIds.isEmpty()) {
            return counts;
        }
        ingredientIds.forEach(id -> counts.put(id,0L));
        for (IngredientProductCount row : productRepository.countByIngredientIds(ingredientIds)) {
            counts.put(row.getIngredientId(), row.getProductCount());
        }
        return counts;
    }
}
