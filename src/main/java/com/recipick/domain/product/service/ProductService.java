package com.recipick.domain.product.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

import com.recipick.domain.product.dto.response.ProductSuggestionResponse;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditChange;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditField;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.request.AdminProductSearchRequest;
import com.recipick.domain.product.dto.request.ProductCreateRequest;
import com.recipick.domain.product.dto.request.ProductDiscountBulkRequest;
import com.recipick.domain.product.dto.request.ProductDiscountUpdateRequest;
import com.recipick.domain.product.dto.response.ProductDiscountBulkResponse;
import com.recipick.domain.product.dto.response.ProductDetailResponse;
import com.recipick.domain.product.dto.response.ProductDiscountResponse;
import com.recipick.domain.product.dto.response.ProductIdResponse;
import com.recipick.domain.product.dto.response.ProductPriceResponse;
import com.recipick.domain.product.dto.request.ProductPriceUpdateRequest;
import com.recipick.domain.product.dto.request.ProductSearchRequest;
import com.recipick.domain.product.dto.response.ProductSearchResponse;
import com.recipick.domain.product.dto.response.ProductStatusResponse;
import com.recipick.domain.product.dto.request.ProductStatusUpdateRequest;
import com.recipick.domain.product.dto.request.ProductUpdateRequest;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.CategoryRepository;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductMartId;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.dto.response.AdminProductResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import com.recipick.domain.product.repository.dto.MemberProductSearchCondition;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.response.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.recipick.global.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    // 베스트는 상위 N개만 보여 주는 화면이라 페이지는 이 범위 안에서만 나뉜다
    public static final int BEST_PRODUCT_LIMIT = 100;

    private final AdminAuditRecorder auditRecorder;

    private final ProductRepository productRepository;
    private final MartRepository martRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public PageResponse<AdminProductResponse> getAdminProducts(
        AdminRole role,
        Long adminMartId,
        AdminProductSearchRequest request,
        Pageable pageable
    ) {
        Long martId = resolveMartId(role, adminMartId, request.martId());
        AdminProductSearchCondition scoped = new AdminProductSearchCondition(
            martId,
            request.keyword(),
            request.categoryId(),
            request.ingredientId(),
            request.status()
        );

        LocalDateTime now = LocalDateTime.now(clock);
        return PageResponse.from(
            productRepository.searchAdminProducts(scoped, pageable)
                .map(product -> AdminProductResponse.from(product, now))
        );
    }

    @Transactional
    public ProductStatusResponse changeStatus(
        AdminRole role,
        Long adminMartId,
        Long productId,
        ProductStatusUpdateRequest request
    ) {
        Product product = productRepository.findByIdForUpdate(productId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (role == AdminRole.MART_ADMIN && !product.getMart().getId().equals(adminMartId)) {
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }

        var beforeStatus = product.getStatus();
        product.changeStatus(request.status());
        if (beforeStatus != product.getStatus()) {
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.PRODUCT_STATUS_CHANGE, product.getId(),
                product.getMart().getId(),
                Map.of(AdminAuditField.STATUS, new AdminAuditChange(beforeStatus, product.getStatus()))));
        }
        return ProductStatusResponse.from(product);
    }

    @Transactional
    public void deleteProduct(AdminRole role, Long adminMartId, Long productId) {
        Product product = productRepository.findByIdForUpdate(productId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (role == AdminRole.MART_ADMIN && !product.getMart().getId().equals(adminMartId)) {
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }

        product.delete();
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.PRODUCT_DELETE, product.getId(),
            product.getMart().getId(), Map.of(AdminAuditField.DELETED, new AdminAuditChange(false, true))));
    }

    // 장바구니처럼 여러 상품을 한 화면에 그리는 곳에서 쓰므로 IN 쿼리 한 번으로 조회한다. 없는 ID는 결과에서 빠진다.
    public List<ProductSummary> findProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return productRepository.findAllById(productIds).stream()
            .map(p -> ProductSummary.from(p, now))
            .toList();
    }

    // 엔티티를 읽지 않고 값만 조회한다. 같은 트랜잭션에서 뒤이어 재고를 잠그고 차감할 때
    // 미리 읽어 둔 엔티티가 있으면 잠금 조회가 그 엔티티의 이전 재고 값으로 계산하기 때문이다
    public Map<Long, Long> findMartIdsOf(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findMartIdsByIdIn(productIds).stream()
            .collect(Collectors.toMap(ProductMartId::getProductId, ProductMartId::getMartId));
    }

    private Long resolveMartId(AdminRole role, Long adminMartId, Long requestedMartId) {
        if (role == AdminRole.MART_ADMIN) {
            if (requestedMartId != null && !requestedMartId.equals(adminMartId)) {
                throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
            }
            return adminMartId;
        }

        if (requestedMartId != null && !martRepository.existsById(requestedMartId)) {
            throw new BusinessException(ProductErrorCode.MART_NOT_FOUND);
        }
        return requestedMartId;
    }

    @Transactional
    public ProductIdResponse createProduct(
        AdminRole role,
        Long adminMartId,
        ProductCreateRequest request,
        SaleUnit ingredientUnit
    ) {
        if (role == AdminRole.MART_ADMIN && !request.martId().equals(adminMartId)) {
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }
        if (request.saleUnitAmount().signum() <= 0) {
            throw new BusinessException(ProductErrorCode.INVALID_SALE_UNIT);
        }
        if (request.price() < 0) {
            throw new BusinessException(ProductErrorCode.INVALID_PRICE);
        }

        Mart mart = martRepository.findByIdForUpdate(request.martId())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.MART_NOT_FOUND));
        Category category = categoryRepository.findById(request.categoryId())
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));

        if (ingredientUnit != null && ingredientUnit != request.saleUnit()) {
            throw new BusinessException(ProductErrorCode.UNIT_MISMATCH);
        }
        if (productRepository.existsByMartIdAndNameAndBrandAndDeletedFalse(
            mart.getId(), request.name(), request.brand())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        Product product = Product.builder()
            .mart(mart)
            .category(category)
            .ingredientId(request.ingredientId())
            .name(request.name())
            .brand(request.brand())
            .description(request.description())
            .saleUnitAmount(request.saleUnitAmount())
            .saleUnit(request.saleUnit())
            .imageUrl(request.imageUrl())
            .price(request.price())
            .build();
        Product saved = productRepository.save(product);
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.PRODUCT_CREATE, saved.getId(), mart.getId(),
            Map.of()));
        return new ProductIdResponse(saved.getId());
    }

    @Transactional
    public ProductIdResponse updateProduct(
        AdminRole role,
        Long adminMartId,
        Long productId,
        ProductUpdateRequest request,
        SaleUnit ingredientUnit
    ) {
        Product product = getProductForUpdate(role, adminMartId, productId);

        if (request.saleUnitAmount().signum() <= 0) {
            throw new BusinessException(ProductErrorCode.INVALID_SALE_UNIT);
        }

        Category category = categoryRepository.findById(request.categoryId())
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));

        if (ingredientUnit != null && ingredientUnit != request.saleUnit()) {
            throw new BusinessException(ProductErrorCode.UNIT_MISMATCH);
        }

        boolean nameOrBrandChanged = !Objects.equals(product.getName(), request.name()) ||
            !Objects.equals(product.getBrand(), request.brand());
        if (nameOrBrandChanged) {
            martRepository.findByIdForUpdate(product.getMart().getId());
            if (productRepository.existsByMartIdAndNameAndBrandAndDeletedFalseAndIdNot(
                product.getMart().getId(), request.name(), request.brand(), productId)) {
                throw new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATED);
            }
        }

        var before = auditValues(product);
        var beforeCategory = product.getCategory().getId();
        var beforeIngredient = product.getIngredientId();
        var beforeAmount = product.getSaleUnitAmount();
        var beforeUnit = product.getSaleUnit();
        product.updateInfo(category, request.ingredientId(), request.name(), request.brand(),
            request.description(), request.saleUnitAmount(), request.saleUnit(), request.imageUrl());
        if (!before.equals(auditValues(product))) {
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.PRODUCT_UPDATE, product.getId(),
                    product.getMart().getId(), Map.of(
                    AdminAuditField.CATEGORY_ID, new AdminAuditChange(beforeCategory, product.getCategory().getId()),
                    AdminAuditField.INGREDIENT_ID, new AdminAuditChange(beforeIngredient, product.getIngredientId()),
                    AdminAuditField.SALE_UNIT_AMOUNT, new AdminAuditChange(beforeAmount, product.getSaleUnitAmount()),
                    AdminAuditField.SALE_UNIT, new AdminAuditChange(beforeUnit, product.getSaleUnit()))));
        }
        return new ProductIdResponse(product.getId());
    }

    @Transactional
    public ProductPriceResponse updatePrice(
        AdminRole role,
        Long adminMartId,
        Long productId,
        ProductPriceUpdateRequest request
    ) {
        Product product = getProductForUpdate(role, adminMartId, productId);
        Long beforePrice = product.getPrice();
        product.changePrice(request.price());
        if (!Objects.equals(beforePrice, product.getPrice())) {
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.PRODUCT_PRICE_CHANGE, product.getId(),
                product.getMart().getId(),
                Map.of(AdminAuditField.PRICE, new AdminAuditChange(beforePrice, product.getPrice()))));
        }
        return ProductPriceResponse.from(product);
    }

    @Transactional
    public ProductDiscountResponse updateDiscount(
        AdminRole role,
        Long adminMartId,
        Long productId,
        ProductDiscountUpdateRequest request
    ) {
        Product product = getProductForUpdate(role, adminMartId, productId);
        int beforeRate = product.getDiscountRate();
        LocalDateTime beforeEndAt = product.getDiscountEndAt();
        LocalDateTime now = LocalDateTime.now(clock);
        product.changeDiscount(request.discountRate(), request.discountEndAt(), now);
        recordDiscountChange(product, beforeRate, beforeEndAt);
        return ProductDiscountResponse.from(product, now);
    }

    // 여러 상품에 같은 할인을 한 번에 지정한다. 하나라도 지정할 수 없으면 전체를 되돌린다.
    // 잠금은 상품 ID 오름차순으로 잡는다(findAllByIdInForUpdate).
    @Transactional
    public ProductDiscountBulkResponse updateDiscountBulk(
        AdminRole role,
        Long adminMartId,
        ProductDiscountBulkRequest request
    ) {
        List<Long> ids = request.productIds().stream().distinct().toList();
        List<Product> products = productRepository.findAllByIdInForUpdate(ids);
        if (products.size() != ids.size() || products.stream().anyMatch(Product::isDeleted)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        if (role == AdminRole.MART_ADMIN
            && products.stream().anyMatch(p -> !p.getMart().getId().equals(adminMartId))) {
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (Product product : products) {
            int beforeRate = product.getDiscountRate();
            LocalDateTime beforeEndAt = product.getDiscountEndAt();
            product.changeDiscount(request.discountRate(), request.discountEndAt(), now);
            recordDiscountChange(product, beforeRate, beforeEndAt);
        }
        return new ProductDiscountBulkResponse(products.size());
    }

    @Transactional
    public ProductDiscountResponse clearDiscount(AdminRole role, Long adminMartId, Long productId) {
        Product product = getProductForUpdate(role, adminMartId, productId);
        int beforeRate = product.getDiscountRate();
        LocalDateTime beforeEndAt = product.getDiscountEndAt();
        product.clearDiscount();
        recordDiscountChange(product, beforeRate, beforeEndAt);
        return ProductDiscountResponse.from(product, LocalDateTime.now(clock));
    }

    private void recordDiscountChange(Product product, int beforeRate, LocalDateTime beforeEndAt) {
        if (beforeRate == product.getDiscountRate() && Objects.equals(beforeEndAt, product.getDiscountEndAt())) {
            return;
        }
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.PRODUCT_DISCOUNT_CHANGE, product.getId(),
            product.getMart().getId(), Map.of(
            AdminAuditField.DISCOUNT_RATE, new AdminAuditChange(beforeRate, product.getDiscountRate()),
            AdminAuditField.DISCOUNT_END_AT, new AdminAuditChange(beforeEndAt, product.getDiscountEndAt()))));
    }

    private Product getProductForUpdate(AdminRole role, Long adminMartId, Long productId) {
        Product product = productRepository.findByIdForUpdate(productId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (role == AdminRole.MART_ADMIN && !product.getMart().getId().equals(adminMartId)) {
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }
        return product;
    }

    private static final Set<String> SEARCH_SORT_PROPERTIES = Set.of("price", "createdAt", "discountEndAt");


    @Cacheable(
        cacheNames = CacheConfig.PRODUCT_SEARCH,
        key = "#martId + ':' + (#request.keyword == null ? '-' : #request.keyword.length() + '#' + #request.keyword)"
            + " + ':' + #request.categoryId() + ':' + #request.minPrice"
            + " + ':' + #request.maxPrice + ':' + #request.onSale + ':' + #pageable.pageNumber + ':' + #pageable.pageSize"
            + " + ':' + #pageable.sort"
    )
    public PageResponse<ProductSearchResponse> searchProducts(
        Long martId,
        ProductSearchRequest request,
        Pageable pageable
    ) {
        if (request.minPrice() != null && request.maxPrice() != null
            && request.minPrice() > request.maxPrice()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SEARCH_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        MemberProductSearchCondition condition = new MemberProductSearchCondition(
            martId, request.keyword(), request.categoryId(), request.minPrice(), request.maxPrice(), now,
            Boolean.TRUE.equals(request.onSale()));
        return PageResponse.from(
            productRepository.searchMemberProducts(condition, pageable)
                .map(product -> ProductSearchResponse.from(product, now))
        );
    }

    private static final int SUGGESTION_LIMIT = 8;
    private static final int MAX_SUGGESTION_KEYWORD_LENGTH = 50;

    // 키 입력마다 오는 요청이라 30초 캐시한다. 비교는 DB에서 하고 8건만 가져온다
    @Cacheable(
        cacheNames = CacheConfig.PRODUCT_SUGGESTIONS,
        key = "#martId + ':' + #keyword",
        condition = "#keyword != null && #keyword.length() <= 50"
    )
    public List<ProductSuggestionResponse> findSuggestions(Long martId, String keyword) {
        String query = keyword == null ? "" : keyword.replace(" ", "").toLowerCase(Locale.ROOT);
        if (query.isEmpty() || query.length() > MAX_SUGGESTION_KEYWORD_LENGTH) {
            return List.of();
        }
        return productRepository.findSuggestions(martId, query, SUGGESTION_LIMIT).stream()
            .map(ProductSuggestionResponse::from)
            .toList();
    }

    public ProductDetailResponse getProduct(Long martId, Long productId) {
        Product product = productRepository.findById(productId)
            .filter(p -> !p.isDeleted() && p.getMart().getId().equals(martId))
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return ProductDetailResponse.from(product, LocalDateTime.now(clock));
    }
    // 같은 값 재요청은 제외하되 문자열 원문은 감사 details에 저장하지 않는다.
    private List<Object> auditValues(Product product) {
        return Arrays.asList(product.getCategory().getId(), product.getIngredientId(), product.getName(),
                product.getBrand(), product.getDescription(),
                product.getSaleUnitAmount() == null ? null : product.getSaleUnitAmount().stripTrailingZeros(),
                product.getSaleUnit(), product.getImageUrl());
    }

    // 순위 ID는 주문 도메인이 정한다. 삭제·다른 마트 상품을 먼저 거른 목록을 기준으로 페이지를 잘라서
    // 페이지가 덜 차거나 전체 개수가 실제보다 커지지 않는다
    public PageResponse<ProductSearchResponse> findBestProducts(Long martId, List<Long> rankedIds, Pageable pageable) {
        List<ProductSearchResponse> ranked = List.of();
        LocalDateTime now = LocalDateTime.now(clock);
        if (!rankedIds.isEmpty()) {
            Map<Long, Product> productById = productRepository
                .findAllByIdInAndMartIdAndDeletedFalse(rankedIds, martId).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
            ranked = rankedIds.stream()
                .map(productById::get)
                .filter(Objects::nonNull)
                .map(product -> ProductSearchResponse.from(product, now))
                .toList();
        }
        int from = (int) Math.min(pageable.getOffset(), ranked.size());
        int to = Math.min(from + pageable.getPageSize(), ranked.size());
        return PageResponse.from(new PageImpl<>(ranked.subList(from, to), pageable, ranked.size()));
    }

}
