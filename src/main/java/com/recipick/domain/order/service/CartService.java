package com.recipick.domain.order.service;

import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.global.exception.BusinessException;
import java.util.List;
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

    // 재시도해도 같은 결과가 나오도록, 이미 없거나 다른 회원의 ID는 건너뛰고 성공 처리한다.
    @Transactional
    public void deleteCartItems(Long memberId, List<Long> cartItemIds) {
        cartItemRepository.deleteByMemberIdAndIdIn(memberId, cartItemIds);
    }
}
