package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.PointHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

// 정렬은 호출자가 넘긴 Pageable로 정한다. 회원 조건이 항상 붙어 다른 회원의 주문 ID를 넣어도 남의 이력은 나오지 않는다.
// 응답에 적립 건 ID를 넣으므로 적립 건을 한 번에 함께 읽어 이력마다 따로 조회하지 않게 한다(다대일이라 페이지 조회에 안전하다)
public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    @EntityGraph(attributePaths = "pointEarning")
    Page<PointHistory> findByMemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = "pointEarning")
    Page<PointHistory> findByMemberIdAndOrderId(Long memberId, Long orderId, Pageable pageable);
}
