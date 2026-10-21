package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CART_ITEM_ID = 100L;

    @Mock
    private CartItemRepository cartItemRepository;

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
}
