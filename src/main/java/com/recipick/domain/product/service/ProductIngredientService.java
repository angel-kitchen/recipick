package com.recipick.domain.product.service;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.IngredientProductCount;
import com.recipick.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public Map<Long, List<ProductSummary>> findCandidates(Long martId, Collection<Long> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllByMartIdAndIngredientIdIn(martId, ingredientIds).stream()
            .collect(Collectors.groupingBy(
                Product::getIngredientId,
                Collectors.mapping(ProductSummary::from, Collectors.toList())
            ));
    }
}
