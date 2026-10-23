package com.recipick.domain.product.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.product.dto.ProductDetailResponse;
import com.recipick.domain.product.dto.ProductSearchRequest;
import com.recipick.domain.product.dto.ProductSearchResponse;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.service.ProductService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private MemberService memberService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductFacade productFacade;

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
