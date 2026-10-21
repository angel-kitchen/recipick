package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // 조회 후 삭제하면 연타로 두 요청이 같은 행을 지울 때 늦은 쪽이 500이 되므로, 한 문장으로 지우고 삭제 건수로 판단한다.
    @Modifying
    @Query("delete from CartItem c where c.id = :id and c.memberId = :memberId")
    int deleteByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);
}
