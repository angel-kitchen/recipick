package com.recipick.domain.product.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.order.service.OrderSalesService;
import com.recipick.domain.product.dto.ProductDetailResponse;
import com.recipick.domain.product.dto.ProductSearchRequest;
import com.recipick.domain.product.dto.ProductSearchResponse;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.service.ProductService;
import com.recipick.global.common.RankingPeriod;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private MemberService memberService;

    @Mock
    private ProductService productService;

    @Mock
    private OrderSalesService orderSalesService;

    // 기준 시각을 고정해 기간 계산을 비교한다. 2026-11-03 09:00 (KST)
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        productFacade = new ProductFacade(memberService, productService, orderSalesService, clock);
    }

    private final Pageable pageable = PageRequest.of(0, 20);
    private final ProductSearchRequest request = new ProductSearchRequest(null, null, null, null);

    @Test
    @DisplayName("검색 : 회원의 이용 마트로 상품 서비스를 호출한다")
    void searchProducts() {
        // given
        PageResponse<ProductSearchResponse> expected = new PageResponse<>(List.of(), 0, 20, 0, 0);
        given(memberService.getMartId(7L)).willReturn(Optional.of(1L));
        given(productService.searchProducts(1L, request, pageable)).willReturn(expected);

        // when
        PageResponse<ProductSearchResponse> result = productFacade.searchProducts(7L, request, pageable);

        // then
        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("상세 : 회원의 이용 마트로 상품 서비스를 호출한다")
    void getProduct() {
        // given
        ProductDetailResponse expected = new ProductDetailResponse(
            5L, "애호박", null, null, "채소", null, null, null, 1500L, null, false);
        given(memberService.getMartId(7L)).willReturn(Optional.of(1L));
        given(productService.getProduct(1L, 5L)).willReturn(expected);

        // when
        ProductDetailResponse result = productFacade.getProduct(7L, 5L);

        // then
        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("베스트 : 이용 마트와 기간 시작 시각으로 상위 순위를 구해 상품 서비스에 페이지 정보와 함께 넘긴다")
    void findBestProducts() {
        // given
        Pageable requested = PageRequest.of(1, 10, Sort.by("price"));
        List<Long> rankedIds = List.of(3L, 1L);
        PageResponse<ProductSearchResponse> response = new PageResponse<>(List.of(), 1, 10, 12, 2);
        given(memberService.getMartId(7L)).willReturn(Optional.of(1L));
        given(orderSalesService.findBestSellingProductIds(
            1L, LocalDateTime.of(2026, 10, 27, 9, 0), ProductService.BEST_PRODUCT_LIMIT))
            .willReturn(rankedIds);
        given(productService.findBestProducts(1L, rankedIds, requested)).willReturn(response);

        // when
        PageResponse<ProductSearchResponse> result =
            productFacade.findBestProducts(7L, RankingPeriod.WEEKLY, requested);

        // then
        assertThat(result).isSameAs(response);
    }

    @Test
    @DisplayName("베스트 : 이용 마트가 없으면 MART_NOT_SELECTED이고 판매량을 조회하지 않는다")
    void findBestProducts_noMart() {
        // given
        given(memberService.getMartId(7L)).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> productFacade.findBestProducts(7L, RankingPeriod.ALL, pageable));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.MART_NOT_SELECTED);
        verifyNoInteractions(orderSalesService, productService);
    }

    @Test
    @DisplayName("이용 마트가 없으면 MART_NOT_SELECTED이고 상품 서비스를 부르지 않는다")
    void noMart() {
        // given
        given(memberService.getMartId(7L)).willReturn(Optional.empty());

        // when
        Throwable searchThrown = catchThrowable(() -> productFacade.searchProducts(7L, request, pageable));
        Throwable detailThrown = catchThrowable(() -> productFacade.getProduct(7L, 5L));

        // then
        assertThat(searchThrown).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.MART_NOT_SELECTED);
        assertThat(detailThrown).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.MART_NOT_SELECTED);
        verifyNoInteractions(productService);
    }
}
