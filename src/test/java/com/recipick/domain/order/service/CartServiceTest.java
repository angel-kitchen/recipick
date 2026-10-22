package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.dto.response.CartItemAddResponse;
import com.recipick.domain.order.dto.response.CartItemQuantityUpdateResponse;
import com.recipick.domain.order.entity.CartAddHistory;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.enums.CartAddSource;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.CartAddHistoryRepository;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CART_ITEM_ID = 100L;
    private static final Long PRODUCT_ID = 10L;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartAddHistoryRepository cartAddHistoryRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    @DisplayName("개별 삭제: 본인 항목이 삭제되면 예외 없이 끝난다")
    void deleteCartItem() {
        // given
        given(cartItemRepository.deleteByIdAndMemberId(CART_ITEM_ID, MEMBER_ID)).willReturn(1);

        // when, then
        assertThatCode(() -> cartService.deleteCartItem(MEMBER_ID, CART_ITEM_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("개별 삭제: 없거나 다른 회원의 항목이면 CART_ITEM_NOT_FOUND")
    void deleteCartItemNotFound() {
        // given
        given(cartItemRepository.deleteByIdAndMemberId(CART_ITEM_ID, MEMBER_ID)).willReturn(0);

        // when, then
        assertThatThrownBy(() -> cartService.deleteCartItem(MEMBER_ID, CART_ITEM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("일괄 삭제: 지워진 항목이 없어도 예외 없이 끝난다")
    void deleteCartItemsWithoutMatch() {
        // given
        List<Long> cartItemIds = List.of(CART_ITEM_ID, CART_ITEM_ID + 1);
        given(cartItemRepository.deleteByMemberIdAndIdIn(MEMBER_ID, cartItemIds)).willReturn(0);

        // when, then
        assertThatCode(() -> cartService.deleteCartItems(MEMBER_ID, cartItemIds)).doesNotThrowAnyException();
        verify(cartItemRepository).deleteByMemberIdAndIdIn(MEMBER_ID, cartItemIds);
    }

    @Test
    @DisplayName("담기: 이미 담긴 상품이면 수량을 합산하고 이번 추가 수량으로 이력을 남긴다")
    void addCartItemMerge() {
        // given
        given(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID))
                .willReturn(Optional.of(cartItem(2)), Optional.of(cartItem(5)));
        given(cartItemRepository.increaseQuantity(
                eq(MEMBER_ID), eq(PRODUCT_ID), eq(3), eq(50), any(LocalDateTime.class))).willReturn(1);

        // when
        CartItemAddResponse response = cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 3, 50);

        // then
        assertThat(response.cartItemId()).isEqualTo(CART_ITEM_ID);
        assertThat(response.quantity()).isEqualTo(5);
        verify(cartItemRepository, never()).saveAndFlush(any());
        assertHistorySaved(3);
    }

    @Test
    @DisplayName("담기: 처음 담는 상품이면 새 항목을 만들고 이력을 남긴다")
    void addCartItemNew() {
        // given
        given(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID)).willReturn(Optional.empty());
        given(cartItemRepository.saveAndFlush(any(CartItem.class))).willReturn(cartItem(3));

        // when
        CartItemAddResponse response = cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 3, 200);

        // then
        assertThat(response.quantity()).isEqualTo(3);
        verify(cartItemRepository, never()).increaseQuantity(any(), any(), anyInt(), anyInt(), any());
        assertHistorySaved(3);
    }

    @Test
    @DisplayName("담기: 합산 결과가 99개를 넘으면 INVALID_CART_QUANTITY")
    void addCartItemOverMaxQuantity() {
        // given
        given(cartItemRepository.increaseQuantity(any(), any(), anyInt(), anyInt(), any())).willReturn(0);
        given(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID))
                .willReturn(Optional.of(cartItem(98)));

        // when, then
        assertThatThrownBy(() -> cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 2, 200))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_CART_QUANTITY);
        verify(cartAddHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("담기: 합산 결과가 재고를 넘으면 CART_INSUFFICIENT_STOCK")
    void addCartItemOverStock() {
        // given
        given(cartItemRepository.increaseQuantity(any(), any(), anyInt(), anyInt(), any())).willReturn(0);
        given(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID))
                .willReturn(Optional.of(cartItem(4)));

        // when, then
        assertThatThrownBy(() -> cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 2, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CART_INSUFFICIENT_STOCK);
        verify(cartAddHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("담기: 처음 담는 수량이 재고를 넘으면 CART_INSUFFICIENT_STOCK")
    void addCartItemNewOverStock() {
        // given
        given(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 6, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CART_INSUFFICIENT_STOCK);
        verify(cartItemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("담기: 동시에 처음 담아 UNIQUE에 걸리면 CART_CONCURRENT_MODIFICATION")
    void addCartItemUniqueConflict() {
        // given
        given(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID)).willReturn(Optional.empty());
        given(cartItemRepository.saveAndFlush(any(CartItem.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        // when, then
        assertThatThrownBy(() -> cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 1, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CART_CONCURRENT_MODIFICATION);
        verify(cartAddHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("담기: 읽은 뒤 다른 요청이 항목을 바꿔 합산하지 못하면 CART_CONCURRENT_MODIFICATION")
    void addCartItemChangedConcurrently() {
        // given
        given(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID))
                .willReturn(Optional.of(cartItem(2)));
        given(cartItemRepository.increaseQuantity(any(), any(), anyInt(), anyInt(), any())).willReturn(0);

        // when, then
        assertThatThrownBy(() -> cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 1, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CART_CONCURRENT_MODIFICATION);
        verify(cartAddHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("수량 변경: 최종 수량으로 바꾸고 이력은 남기지 않는다")
    void updateCartItemQuantity() {
        // given
        given(cartItemRepository.updateQuantity(
                eq(CART_ITEM_ID), eq(MEMBER_ID), eq(5), any(LocalDateTime.class))).willReturn(1);

        // when
        CartItemQuantityUpdateResponse response = cartService.updateCartItemQuantity(MEMBER_ID, CART_ITEM_ID, 5, 30);

        // then
        assertThat(response).isEqualTo(new CartItemQuantityUpdateResponse(CART_ITEM_ID, 5));
        verify(cartAddHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("수량 변경: 재고보다 많으면 CART_INSUFFICIENT_STOCK")
    void updateCartItemQuantityOverStock() {
        // when, then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(MEMBER_ID, CART_ITEM_ID, 6, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CART_INSUFFICIENT_STOCK);
        verify(cartItemRepository, never()).updateQuantity(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("수량 변경: 읽은 뒤 다른 요청이 항목을 지워 갱신되지 않으면 CART_ITEM_NOT_FOUND")
    void updateCartItemQuantityDeletedConcurrently() {
        // given
        given(cartItemRepository.updateQuantity(any(), any(), anyInt(), any())).willReturn(0);

        // when, then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(MEMBER_ID, CART_ITEM_ID, 5, 30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CART_ITEM_NOT_FOUND);
    }

    private CartItem cartItem(int quantity) {
        CartItem cartItem = CartItem.builder()
                .memberId(MEMBER_ID)
                .productId(PRODUCT_ID)
                .quantity(quantity)
                .build();
        ReflectionTestUtils.setField(cartItem, "id", CART_ITEM_ID);
        return cartItem;
    }

    private void assertHistorySaved(int quantity) {
        ArgumentCaptor<CartAddHistory> captor = ArgumentCaptor.forClass(CartAddHistory.class);
        verify(cartAddHistoryRepository).save(captor.capture());
        CartAddHistory history = captor.getValue();
        assertThat(history.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(history.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(history.getSource()).isEqualTo(CartAddSource.DIRECT);
        assertThat(history.getQuantity()).isEqualTo(quantity);
    }
}
