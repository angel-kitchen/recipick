package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.order.dto.request.CartItemAddRequest;
import com.recipick.domain.order.dto.request.CartItemQuantityUpdateRequest;
import com.recipick.domain.order.dto.response.CartItemAddResponse;
import com.recipick.domain.order.dto.response.CartItemQuantityUpdateResponse;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.service.ProductService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CartFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long MART_ID = 7L;
    private static final Long OTHER_MART_ID = 8L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CART_PRODUCT_ID = 11L;
    private static final Long CART_ITEM_ID = 100L;
    private static final CartItemAddRequest REQUEST = new CartItemAddRequest(PRODUCT_ID, 2);
    private static final CartItemQuantityUpdateRequest UPDATE_REQUEST = new CartItemQuantityUpdateRequest(5);

    @Mock
    private MemberService memberService;

    @Mock
    private ProductService productService;

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartFacade cartFacade;

    @Test
    @DisplayName("이용 마트의 판매 중인 상품이면 장바구니에 담는다")
    void addCartItem() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.SELLING, 30, false)));
        given(cartService.findProductIds(MEMBER_ID)).willReturn(List.of(CART_PRODUCT_ID));
        given(productService.findProducts(List.of(CART_PRODUCT_ID)))
                .willReturn(List.of(product(CART_PRODUCT_ID, MART_ID, ProductStatus.STOPPED, 0, false)));
        CartItemAddResponse expected = new CartItemAddResponse(100L, PRODUCT_ID, 2);
        given(cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 2, 30)).willReturn(expected);

        // when
        CartItemAddResponse response = cartFacade.addCartItem(MEMBER_ID, REQUEST);

        // then
        assertThat(response).isEqualTo(expected);
    }

    @Test
    @DisplayName("이용 마트가 없으면 MART_NOT_SELECTED")
    void addCartItemWithoutMart() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.empty());

        // when, then
        assertErrorCode(OrderErrorCode.MART_NOT_SELECTED);
        verifyNoInteractions(productService, cartService);
    }

    @Test
    @DisplayName("상품이 없으면 PRODUCT_NOT_FOUND")
    void addCartItemProductNotFound() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID))).willReturn(List.of());

        // when, then
        assertErrorCode(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 상품이면 PRODUCT_NOT_FOUND")
    void addCartItemDeletedProduct() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.SELLING, 30, true)));

        // when, then
        assertErrorCode(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 마트의 상품이면 CART_ITEM_NOT_PURCHASABLE")
    void addCartItemOtherMartProduct() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, OTHER_MART_ID, ProductStatus.SELLING, 30, false)));

        // when, then
        assertErrorCode(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("판매 중지 상품이면 CART_ITEM_NOT_PURCHASABLE")
    void addCartItemStoppedProduct() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.STOPPED, 30, false)));

        // when, then
        assertErrorCode(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("품절 상품이면 CART_ITEM_NOT_PURCHASABLE")
    void addCartItemSoldOutProduct() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.SELLING, 0, false)));

        // when, then
        assertErrorCode(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("장바구니에 이전 마트 상품이 남아 있으면 CART_HAS_PREVIOUS_MART_ITEMS")
    void addCartItemWithPreviousMartItems() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.SELLING, 30, false)));
        given(cartService.findProductIds(MEMBER_ID)).willReturn(List.of(CART_PRODUCT_ID));
        given(productService.findProducts(List.of(CART_PRODUCT_ID)))
                .willReturn(List.of(product(CART_PRODUCT_ID, OTHER_MART_ID, ProductStatus.SELLING, 30, false)));

        // when, then
        assertErrorCode(OrderErrorCode.CART_HAS_PREVIOUS_MART_ITEMS);
    }

    @Test
    @DisplayName("수량 변경: 이용 마트의 판매 중인 상품이면 최종 수량으로 바꾼다")
    void updateCartItemQuantity() {
        // given
        given(cartService.getCartItem(MEMBER_ID, CART_ITEM_ID)).willReturn(cartItem());
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.SELLING, 30, false)));
        CartItemQuantityUpdateResponse expected = new CartItemQuantityUpdateResponse(CART_ITEM_ID, 5);
        given(cartService.updateCartItemQuantity(MEMBER_ID, CART_ITEM_ID, 5, 30)).willReturn(expected);

        // when
        CartItemQuantityUpdateResponse response =
                cartFacade.updateCartItemQuantity(MEMBER_ID, CART_ITEM_ID, UPDATE_REQUEST);

        // then
        assertThat(response).isEqualTo(expected);
    }

    @Test
    @DisplayName("수량 변경: 이전 마트 상품이면 CART_ITEM_NOT_PURCHASABLE")
    void updateCartItemQuantityPreviousMartProduct() {
        // given
        given(cartService.getCartItem(MEMBER_ID, CART_ITEM_ID)).willReturn(cartItem());
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, OTHER_MART_ID, ProductStatus.SELLING, 30, false)));

        // when, then
        assertUpdateErrorCode(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("수량 변경: 판매 중지 상품이면 CART_ITEM_NOT_PURCHASABLE")
    void updateCartItemQuantityStoppedProduct() {
        // given
        given(cartService.getCartItem(MEMBER_ID, CART_ITEM_ID)).willReturn(cartItem());
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.STOPPED, 30, false)));

        // when, then
        assertUpdateErrorCode(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("수량 변경: 품절 상품이면 CART_ITEM_NOT_PURCHASABLE")
    void updateCartItemQuantitySoldOutProduct() {
        // given
        given(cartService.getCartItem(MEMBER_ID, CART_ITEM_ID)).willReturn(cartItem());
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.SELLING, 0, false)));

        // when, then
        assertUpdateErrorCode(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("수량 변경: 담긴 뒤 삭제된 상품이면 CART_ITEM_NOT_PURCHASABLE")
    void updateCartItemQuantityDeletedProduct() {
        // given
        given(cartService.getCartItem(MEMBER_ID, CART_ITEM_ID)).willReturn(cartItem());
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(productService.findProducts(List.of(PRODUCT_ID)))
                .willReturn(List.of(product(PRODUCT_ID, MART_ID, ProductStatus.SELLING, 30, true)));

        // when, then
        assertUpdateErrorCode(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("수량 변경: 이용 마트가 없으면 MART_NOT_SELECTED")
    void updateCartItemQuantityWithoutMart() {
        // given
        given(cartService.getCartItem(MEMBER_ID, CART_ITEM_ID)).willReturn(cartItem());
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.empty());

        // when, then
        assertUpdateErrorCode(OrderErrorCode.MART_NOT_SELECTED);
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("수량 변경: 없거나 다른 회원의 항목이면 CART_ITEM_NOT_FOUND")
    void updateCartItemQuantityNotFound() {
        // given
        given(cartService.getCartItem(MEMBER_ID, CART_ITEM_ID))
                .willThrow(new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND));

        // when, then
        assertUpdateErrorCode(OrderErrorCode.CART_ITEM_NOT_FOUND);
        verify(cartService, never()).updateCartItemQuantity(any(), any(), anyInt(), anyInt());
    }

    private void assertErrorCode(ErrorCode errorCode) {
        assertThatThrownBy(() -> cartFacade.addCartItem(MEMBER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private void assertUpdateErrorCode(ErrorCode errorCode) {
        assertThatThrownBy(() -> cartFacade.updateCartItemQuantity(MEMBER_ID, CART_ITEM_ID, UPDATE_REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private CartItem cartItem() {
        CartItem cartItem = CartItem.builder()
                .memberId(MEMBER_ID)
                .productId(PRODUCT_ID)
                .quantity(2)
                .build();
        ReflectionTestUtils.setField(cartItem, "id", CART_ITEM_ID);
        return cartItem;
    }

    private ProductSummary product(Long productId, Long martId, ProductStatus status, int stock, boolean deleted) {
        return new ProductSummary(productId, martId, "애호박", null, 1500L, null, null, status, stock, deleted);
    }
}
