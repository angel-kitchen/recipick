package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.CartItem;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByMemberIdAndProductId(Long memberId, Long productId);

    Optional<CartItem> findByIdAndMemberId(Long id, Long memberId);

    List<CartItem> findAllByMemberIdAndIdIn(Long memberId, Collection<Long> ids);

    List<CartItem> findAllByMemberIdOrderByIdAsc(Long memberId);

    @Query("select c.productId from CartItem c where c.memberId = :memberId")
    List<Long> findProductIdsByMemberId(@Param("memberId") Long memberId);

    // 조회 후 합산하면 동시 요청끼리 수량을 덮어쓰므로, 한 문장으로 더하고 갱신 건수로 판단한다.
    // 벌크 UPDATE는 Auditing을 거치지 않아 updated_at을 직접 갱신한다.
    // 먼저 읽어 둔 엔티티가 갱신 전 수량으로 남지 않도록 영속성 컨텍스트를 비운다.
    @Modifying(clearAutomatically = true)
    @Query("update CartItem c set c.quantity = c.quantity + :quantity, c.updatedAt = :now "
            + "where c.memberId = :memberId and c.productId = :productId and c.quantity + :quantity <= :maxQuantity")
    int increaseQuantity(
            @Param("memberId") Long memberId,
            @Param("productId") Long productId,
            @Param("quantity") int quantity,
            @Param("maxQuantity") int maxQuantity,
            @Param("now") LocalDateTime now
    );

    // 읽은 뒤 다른 요청이 항목을 지우면 변경 감지 UPDATE가 0건이 되어 500이 나므로, 한 문장으로 바꾸고 갱신 건수로 판단한다.
    @Modifying
    @Query("update CartItem c set c.quantity = :quantity, c.updatedAt = :now "
            + "where c.id = :id and c.memberId = :memberId")
    int updateQuantity(
            @Param("id") Long id,
            @Param("memberId") Long memberId,
            @Param("quantity") int quantity,
            @Param("now") LocalDateTime now
    );

    // 조회 후 삭제하면 연타로 두 요청이 같은 행을 지울 때 늦은 쪽이 500이 되므로, 한 문장으로 지우고 삭제 건수로 판단한다.
    @Modifying
    @Query("delete from CartItem c where c.id = :id and c.memberId = :memberId")
    int deleteByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

    @Modifying
    @Query("delete from CartItem c where c.memberId = :memberId and c.id in :ids")
    int deleteByMemberIdAndIdIn(@Param("memberId") Long memberId, @Param("ids") Collection<Long> ids);
}
