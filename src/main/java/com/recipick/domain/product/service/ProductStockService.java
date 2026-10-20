package com.recipick.domain.product.service;

import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductStockService {

    private final ProductRepository productRepository;

    @Transactional
    public void decreaseStocks(Long martId, List<StockQuantity> items) {
        Map<Long, Product> products = lockProducts(items);
        for (StockQuantity item : items) {
            Product product = products.get(item.productId());
            if (product == null || product.isDeleted()
                || product.getMart() == null
                || !product.getMart().getId().equals(martId)) {
                throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
            }
            if (product.getStatus() != ProductStatus.SELLING) {
                throw new BusinessException(ProductErrorCode.PRODUCT_NOT_SELLING);
            }
            product.decreaseStock(item.quantity());
        }
    }

    @Transactional
    public void increaseStocks(List<StockQuantity> items) {
        Map<Long, Product> products = lockProducts(items);
        for (StockQuantity item : items) {
            Product product = products.get(item.productId());
            if (product == null) {
                throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
            }
            product.increaseStock(item.quantity());
        }
    }

    private Map<Long, Product> lockProducts(List<StockQuantity> items) {
        List<Long> ids = items.stream()
            .map(StockQuantity::productId)
            .distinct()
            .toList();
        return productRepository.findAllByIdInForUpdate(ids).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
