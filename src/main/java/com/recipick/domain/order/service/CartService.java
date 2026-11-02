package com.recipick.domain.order.service;

import com.recipick.domain.order.dto.response.CartItemAddResponse;
import com.recipick.domain.order.dto.response.CartItemQuantityUpdateResponse;
import com.recipick.domain.order.entity.CartAddHistory;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.enums.CartAddSource;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.CartAddHistoryRepository;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.global.common.TimeConstants;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private static final int MAX_QUANTITY = 99;

    private final CartItemRepository cartItemRepository;
    private final CartAddHistoryRepository cartAddHistoryRepository;

    public List<Long> findProductIds(Long memberId) {
        return cartItemRepository.findProductIdsByMemberId(memberId);
    }

    public Map<Long, Integer> findQuantities(Long memberId, Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return cartItemRepository.findAllByMemberIdAndProductIdIn(memberId, productIds).stream()
            .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));
    }

    // 상품 판매 가능 여부·마트 검증은 호출하는 Facade가 끝낸 뒤 들어온다.
    @Transactional
    public CartItemAddResponse addCartItem(Long memberId, Long productId, int quantity, int stock) {
        return addCartItem(memberId, productId, quantity, stock, CartAddSource.DIRECT, null);
    }

    // 자동 구성으로 담은 상품을 어느 요청에서 담았는지 담기 이력으로 추적할 수 있게 요청 번호를 함께 남긴다.
    @Transactional
    public CartItemAddResponse addAutoCartItem(
            Long memberId, Long productId, int quantity, int stock, Long autoCartRequestId) {
        return addCartItem(memberId, productId, quantity, stock, CartAddSource.AUTO_CART, autoCartRequestId);
    }

    private CartItemAddResponse addCartItem(
            Long memberId, Long productId, int quantity, int stock, CartAddSource source, Long autoCartRequestId) {
        // 없는 행을 UPDATE하면 MySQL이 갭 락을 잡아 동시 insert끼리 데드락이 나므로, 있을 때만 UPDATE한다.
        CartItem cartItem = cartItemRepository.findByMemberIdAndProductId(memberId, productId)
                .map(existing -> increaseQuantity(existing, quantity, stock))
                .orElseGet(() -> {
                    validateQuantity(quantity, stock);
                    return insertCartItem(memberId, productId, quantity);
                });
        cartAddHistoryRepository.save(CartAddHistory.builder()
                .memberId(memberId)
                .productId(productId)
                .source(source)
                .autoCartRequestId(autoCartRequestId)
                .quantity(quantity)
                .build());
        return CartItemAddResponse.from(cartItem);
    }

    // 담은 순서대로 돌려준다. 같은 상품을 다시 담아 합산해도 행이 그대로라 위치가 바뀌지 않는다
    public List<CartItem> findAllCartItems(Long memberId) {
        return cartItemRepository.findAllByMemberIdOrderByIdAsc(memberId);
    }

    // 요청한 순서대로 돌려준다. 하나라도 없거나 다른 회원 소유면 주문 대상을 확정할 수 없다
    public List<CartItem> findCartItems(Long memberId, List<Long> cartItemIds) {
        Map<Long, CartItem> cartItems = cartItemRepository.findAllByMemberIdAndIdIn(memberId, cartItemIds).stream()
                .collect(Collectors.toMap(CartItem::getId, Function.identity()));
        if (cartItems.size() != cartItemIds.size()) {
            throw new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND);
        }
        return cartItemIds.stream().map(cartItems::get).toList();
    }

    public CartItem getCartItem(Long memberId, Long cartItemId) {
        return cartItemRepository.findByIdAndMemberId(cartItemId, memberId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND));
    }

    // 상품 판매 가능 여부·마트 검증은 호출하는 Facade가 끝낸 뒤 들어온다.
    // 최종 수량으로 덮어쓰므로 같은 요청이 반복되거나 겹쳐도 마지막 요청의 수량이 남는다.
    @Transactional
    public CartItemQuantityUpdateResponse updateCartItemQuantity(
            Long memberId, Long cartItemId, int quantity, int stock) {
        validateQuantity(quantity, stock);
        LocalDateTime now = LocalDateTime.now(TimeConstants.ZONE_ID);
        if (cartItemRepository.updateQuantity(cartItemId, memberId, quantity, now) == 0) {
            throw new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND);
        }
        return new CartItemQuantityUpdateResponse(cartItemId, quantity);
    }

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

    // 탈퇴 트랜잭션 안에서 부른다. 담기 이력은 장바구니 삭제와 같이 남긴다
    @Transactional
    public void deleteAllByMemberId(Long memberId) {
        cartItemRepository.deleteByMemberId(memberId);
    }

    private CartItem increaseQuantity(CartItem existing, int quantity, int stock) {
        LocalDateTime now = LocalDateTime.now(TimeConstants.ZONE_ID);
        int maxQuantity = Math.min(MAX_QUANTITY, stock);
        if (cartItemRepository.increaseQuantity(
                existing.getMemberId(), existing.getProductId(), quantity, maxQuantity, now) == 0) {
            validateQuantity(existing.getQuantity() + quantity, stock);
            // 읽은 뒤 다른 요청이 수량을 바꾸거나 항목을 지웠으므로, 현재 장바구니를 보고 다시 판단하게 한다.
            throw new BusinessException(OrderErrorCode.CART_CONCURRENT_MODIFICATION);
        }
        return cartItemRepository.findByMemberIdAndProductId(existing.getMemberId(), existing.getProductId())
                .orElseThrow();
    }

    private void validateQuantity(int totalQuantity, int stock) {
        if (totalQuantity > MAX_QUANTITY) {
            throw new BusinessException(OrderErrorCode.INVALID_CART_QUANTITY);
        }
        if (totalQuantity > stock) {
            throw new BusinessException(OrderErrorCode.CART_INSUFFICIENT_STOCK);
        }
    }

    // 처음 담는 상품을 연타하면 두 요청이 모두 insert로 와서 UNIQUE에 걸린다.
    // 먼저 들어간 요청이 이미 담았으므로, 재시도로 두 번 합산되지 않게 장바구니 확인을 안내하는 409로 응답한다.
    private CartItem insertCartItem(Long memberId, Long productId, int quantity) {
        try {
            return cartItemRepository.saveAndFlush(CartItem.builder()
                    .memberId(memberId)
                    .productId(productId)
                    .quantity(quantity)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(OrderErrorCode.CART_CONCURRENT_MODIFICATION, e);
        }
    }
}
