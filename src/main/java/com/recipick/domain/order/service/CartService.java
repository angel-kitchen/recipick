package com.recipick.domain.order.service;

import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;

    @Transactional
    public void deleteCartItem(Long memberId, Long cartItemId) {
        if (cartItemRepository.deleteByIdAndMemberId(cartItemId, memberId) == 0) {
            throw new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND);
        }
    }
}
