package com.recipick.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.CartAddHistory;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.enums.CartAddSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CartItemRepositoryTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    CartAddHistoryRepository cartAddHistoryRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("본인 항목은 삭제하고 삭제 건수 1을 돌려준다")
    void deleteByIdAndMemberId() {
        // given
        CartItem saved = cartItemRepository.save(newCartItem(MEMBER_ID));
        em.flush();
        em.clear();

        // when
        int deleted = cartItemRepository.deleteByIdAndMemberId(saved.getId(), MEMBER_ID);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(cartItemRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 회원의 항목은 삭제하지 않고 0을 돌려준다")
    void deleteByIdAndMemberIdOfOtherMember() {
        // given
        CartItem saved = cartItemRepository.save(newCartItem(OTHER_MEMBER_ID));
        em.flush();
        em.clear();

        // when
        int deleted = cartItemRepository.deleteByIdAndMemberId(saved.getId(), MEMBER_ID);

        // then
        assertThat(deleted).isZero();
        assertThat(cartItemRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("항목을 삭제해도 담기 이력은 남는다")
    void deleteKeepsAddHistory() {
        // given
        CartItem saved = cartItemRepository.save(newCartItem(MEMBER_ID));
        CartAddHistory history = cartAddHistoryRepository.save(CartAddHistory.builder()
                .memberId(MEMBER_ID)
                .productId(PRODUCT_ID)
                .source(CartAddSource.DIRECT)
                .quantity(2)
                .build());
        em.flush();
        em.clear();

        // when
        cartItemRepository.deleteByIdAndMemberId(saved.getId(), MEMBER_ID);

        // then
        assertThat(cartAddHistoryRepository.findById(history.getId())).isPresent();
    }

    private CartItem newCartItem(Long memberId) {
        return CartItem.builder()
                .memberId(memberId)
                .productId(PRODUCT_ID)
                .quantity(2)
                .build();
    }
}
