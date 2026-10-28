package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.Refund;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    // 한 결제의 SYSTEM 환불(최대 한 건). 실행 예약·취소 웹훅이 조회 트랜잭션 밖에서 refund.getPayment()를 읽으므로
    // 결제를 함께 읽어 둔다(payment가 LAZY)
    @EntityGraph(attributePaths = "payment")
    @Query("""

        SELECT r
            FROM Refund r
            WHERE r.payment.id = :paymentId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.SYSTEM
            """)
    Optional<Refund> findSystemRefundByPaymentId(@Param("paymentId") Long paymentId);

    // 결제 후 취소의 같은 키 재요청. idempotency_key가 UNIQUE라 최대 한 건이다
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    // 주문 결제 조회용 단건
    List<Refund> findAllByPaymentIdOrderByIdDesc(Long paymentId);

    // 결제 내역용 배치. 결제별 묶음과 최신 판정은 Service가 하므로 정렬은 두지 않는다
    List<Refund> findAllByPaymentIdIn(Collection<Long> paymentIds);

    // 웹훅·재조회가 PortOne 취소 ID로 환불을 찾는다. portone_cancellation_id가 UNIQUE라 최대 한 건이다
    // 정합화 Facade가 조회 트랜잭션 밖에서 refund.getPayment()를 읽으므로 결제를 함께 읽어 둔다(payment가 LAZY)
    @EntityGraph(attributePaths = "payment")
    Optional<Refund> findByPortoneCancellationId(String portoneCancellationId);

    // 취소 ID를 아직 못 받은 회원 환불을 찾는 2차 경로. 정합화는 정확히 한 건일 때만 자동 반영하므로 목록으로 돌려준다
    // 위와 같은 이유로 결제를 함께 읽는다
    @EntityGraph(attributePaths = "payment")
    @Query("""
        SELECT r
            FROM Refund r
            WHERE r.payment.id = :paymentId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.MEMBER
              AND r.status =
                  com.recipick.domain.payment.entity.enums.RefundStatus.REQUESTED
            """)
    List<Refund> findRequestedMemberRefundsByPaymentId(@Param("paymentId") Long paymentId);

    // 취소 ID를 아직 못 받은 마트 거절 환불. 회원 환불과 같은 이유로 목록으로 돌려주고 결제를 함께 읽는다
    @EntityGraph(attributePaths = "payment")
    @Query("""
        SELECT r
            FROM Refund r
            WHERE r.payment.id = :paymentId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.ADMIN
              AND r.status =
                  com.recipick.domain.payment.entity.enums.RefundStatus.REQUESTED
            """)
    List<Refund> findRequestedAdminRefundsByPaymentId(@Param("paymentId") Long paymentId);

    // 실패로 끝난 마트 거절 환불. 콘솔 전액 취소로 거절 주문을 닫을 수 있는지 판정할 때 쓴다
    @Query("""
        SELECT r
            FROM Refund r
            WHERE r.payment.id = :paymentId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.ADMIN
              AND r.status =
                  com.recipick.domain.payment.entity.enums.RefundStatus.FAILED
            """)
    List<Refund> findFailedAdminRefundsByPaymentId(@Param("paymentId") Long paymentId);

    // 같은 결제의 다른 환불에 이미 묶인 PortOne 취소 ID. 상태·요청 주체와 무관하다 —
    // 취소 ID 없는 환불이 PortOne 목록에서 자기 취소를 고를 때 과거 시도의 취소를 후보에서 빼려고 읽는다
    @Query("""
        SELECT r.portoneCancellationId
            FROM Refund r
            WHERE r.payment.id = :paymentId
              AND r.id <> :excludeRefundId
              AND r.portoneCancellationId IS NOT NULL
            """)
    List<String> findBoundCancellationIds(
        @Param("paymentId") Long paymentId,
        @Param("excludeRefundId") Long excludeRefundId);


    // 환불 재확인용. Facade가 조회 트랜잭션 밖에서 결제 ID·PortOne 결제 ID를 읽으므로 결제를 함께 읽는다
    @EntityGraph(attributePaths = "payment")
    Optional<Refund> findWithPaymentById(Long id);

    // Fallback Sweep용. 큐에 다시 넣을 ID만 읽고, id 기준으로 이어 읽어(afterId) 처리 중 행이 바뀌어도 건너뛰지 않는다
    @Query("""
        SELECT r.id
            FROM Refund r
            WHERE r.id > :afterId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.MEMBER
              AND r.status =
                  com.recipick.domain.payment.entity.enums.RefundStatus.REQUESTED
              AND r.requestedAt <= :requestedBefore
            ORDER BY r.id
            """)
    List<Long> findStaleRequestedMemberRefundIds(
        @Param("afterId") Long afterId,
        @Param("requestedBefore") LocalDateTime requestedBefore,
        Pageable pageable);

    // SYSTEM 환불 Fallback Sweep용. 회원 환불과 같은 방식으로 큐에 다시 넣을 ID만 id 순으로 이어 읽는다
    @Query("""
        SELECT r.id
            FROM Refund r
            WHERE r.id > :afterId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.SYSTEM
              AND r.status =
                  com.recipick.domain.payment.entity.enums.RefundStatus.REQUESTED
              AND r.requestedAt <= :requestedBefore
            ORDER BY r.id
            """)
    List<Long> findStaleRequestedSystemRefundIds(
        @Param("afterId") Long afterId,
        @Param("requestedBefore") LocalDateTime requestedBefore,
        Pageable pageable);

    // 마트 거절 환불 Fallback Sweep용. 다른 환불과 같은 방식으로 큐에 다시 넣을 ID만 id 순으로 이어 읽는다
    @Query("""
        SELECT r.id
            FROM Refund r
            WHERE r.id > :afterId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.ADMIN
              AND r.status =
                  com.recipick.domain.payment.entity.enums.RefundStatus.REQUESTED
              AND r.requestedAt <= :requestedBefore
            ORDER BY r.id
            """)
    List<Long> findStaleRequestedAdminRefundIds(
        @Param("afterId") Long afterId,
        @Param("requestedBefore") LocalDateTime requestedBefore,
        Pageable pageable);
}
