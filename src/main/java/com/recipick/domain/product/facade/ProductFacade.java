package com.recipick.domain.product.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.order.service.OrderSalesService;
import com.recipick.domain.product.dto.request.ProductSearchRequest;
import com.recipick.domain.product.dto.response.ProductDetailResponse;
import com.recipick.domain.product.dto.response.ProductSearchResponse;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.service.ProductService;
import com.recipick.global.common.RankingPeriod;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductFacade {

    private final MemberService memberService;
    private final ProductService productService;
    private final OrderSalesService orderSalesService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ProductSearchResponse> searchProducts(
        Long memberId,
        ProductSearchRequest request,
        Pageable pageable
    ) {
        return productService.searchProducts(findMartId(memberId), request, pageable);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(Long memberId, Long productId) {
        return productService.getProduct(findMartId(memberId), productId);
    }

    private Long findMartId(Long memberId) {
        return memberService.getMartId(memberId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.MART_NOT_SELECTED));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSearchResponse> findBestProducts(
        Long memberId, RankingPeriod period, Pageable pageable
    ) {
        Long martId = findMartId(memberId);
        LocalDateTime from = period.startFrom(LocalDateTime.now(clock));
        List<Long> rankedIds = orderSalesService.findBestSellingProductIds(
            martId, from, ProductService.BEST_PRODUCT_LIMIT);
        return productService.findBestProducts(martId, rankedIds, pageable);
    }
}
