package com.recipick.domain.product.service;

import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditField;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.dto.StockAdjustmentHistoryResponse;
import com.recipick.domain.product.dto.StockAdjustmentRequest;
import com.recipick.domain.product.dto.StockAdjustmentResponse;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.entity.StockAdjustment;
import com.recipick.domain.product.entity.StockAdjustmentType;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.repository.StockAdjustmentRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductStockService {

    private final AdminAuditRecorder auditRecorder;

    private final ProductRepository productRepository;

    private final StockAdjustmentRepository stockAdjustmentRepository;

    // 주문은 잠근 상태의 가격으로 금액을 계산해야 해서, 차감한 상품을 요청 순서대로 돌려준다
    @Transactional
    public List<ProductSummary> decreaseStocks(Long martId, List<StockQuantity> items) {
        Map<Long, Product> products = lockProducts(items);
        List<ProductSummary> decreased = new ArrayList<>(items.size());
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
            decreased.add(ProductSummary.from(product));
        }
        return decreased;
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
    public PageResponse<StockAdjustmentHistoryResponse> getStockAdjustments(
        AdminRole role,
        Long adminMartId,
        Long productId,
        Pageable pageable
    ) {
        Product product = productRepository.findById(productId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if(role == AdminRole.MART_ADMIN && !product.getMart().getId().equals(adminMartId)){
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }

        return PageResponse.from(
            stockAdjustmentRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, pageable)
                .map(StockAdjustmentHistoryResponse::from)
        );
    }
    @Transactional
    public StockAdjustmentResponse adjustStock(
        AdminRole role,
        Long adminMartId,
        Long adminId,
        Long productId,
        StockAdjustmentRequest request
    ) {
        Product product =
            productRepository.findByIdForUpdate(productId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if(role == AdminRole.MART_ADMIN && !product.getMart().getId().equals(adminMartId)){
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }

        int quantity = request.quantity();
        int beforeQuantity = product.getTotalQuantity();

        if(request.type() == StockAdjustmentType.INCREASE){
            product.increaseStock(quantity);
        } else {
            if (quantity > beforeQuantity) {
                throw new BusinessException(ProductErrorCode.STOCK_ADJUST_EXCEEDS_AVAILABLE);
            }
            product.decreaseStock(quantity);
            }
        stockAdjustmentRepository.save(StockAdjustment.builder()
            .product(product)
            .type(request.type())
            .quantity(quantity)
            .beforeQuantity(beforeQuantity)
            .reason(request.reason())
            .adminId(adminId)
            .build());

        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.STOCK_ADJUST, product.getId(), product.getMart().getId(), Map.of(AdminAuditField.TOTAL_QUANTITY, new AdminAuditChange(beforeQuantity, product.getTotalQuantity()))));
        return new StockAdjustmentResponse(beforeQuantity,product.getTotalQuantity());
        }
    }
