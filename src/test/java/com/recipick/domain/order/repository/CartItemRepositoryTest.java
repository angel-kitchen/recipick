package com.recipick.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.CartAddHistory;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.enums.CartAddSource;
import com.recipick.global.common.TimeConstants;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
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
    @DisplayName("수량 변경은 본인 항목만 최종 수량으로 바꾸고 갱신 건수를 돌려준다")
    void updateQuantity() {
        // given
        CartItem mine = cartItemRepository.save(newCartItem(MEMBER_ID));
        CartItem others = cartItemRepository.save(newCartItem(OTHER_MEMBER_ID));
        em.flush();
        em.clear();
        LocalDateTime now = LocalDateTime.now(TimeConstants.ZONE_ID);

        // when
        int updated = cartItemRepository.updateQuantity(mine.getId(), MEMBER_ID, 7, now);
        int updatedOthers = cartItemRepository.updateQuantity(others.getId(), MEMBER_ID, 7, now);

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(updatedOthers).isZero();
        assertThat(cartItemRepository.findById(mine.getId()).orElseThrow().getQuantity()).isEqualTo(7);
        assertThat(cartItemRepository.findById(others.getId()).orElseThrow().getQuantity())
                .isEqualTo(others.getQuantity());
    }

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

    @Test
    @DisplayName("일괄 삭제는 본인 항목만 지우고, 다른 회원의 ID와 없는 ID는 건너뛴다")
    void deleteByMemberIdAndIdIn() {
        // given
        CartItem mine1 = cartItemRepository.save(newCartItem(MEMBER_ID, 10L));
        CartItem mine2 = cartItemRepository.save(newCartItem(MEMBER_ID, 11L));
        CartItem mineKept = cartItemRepository.save(newCartItem(MEMBER_ID, 12L));
        CartItem others = cartItemRepository.save(newCartItem(OTHER_MEMBER_ID, 10L));
        em.flush();
        em.clear();
        Long missingId = others.getId() + 1000;

        // when
        int deleted = cartItemRepository.deleteByMemberIdAndIdIn(
                MEMBER_ID, List.of(mine1.getId(), mine2.getId(), others.getId(), missingId));

        // then
        assertThat(deleted).isEqualTo(2);
        assertThat(cartItemRepository.findAllById(List.of(mine1.getId(), mine2.getId()))).isEmpty();
        assertThat(cartItemRepository.findById(mineKept.getId())).isPresent();
        assertThat(cartItemRepository.findById(others.getId())).isPresent();
    }

    private CartItem newCartItem(Long memberId) {
        return newCartItem(memberId, PRODUCT_ID);
    }

    private CartItem newCartItem(Long memberId, Long productId) {
        return CartItem.builder()
                .memberId(memberId)
                .productId(productId)
                .quantity(2)
                .build();
    }
}
