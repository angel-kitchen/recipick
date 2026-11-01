package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.BillingKeyIssueRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingKeyIssueRequestRepository extends JpaRepository<BillingKeyIssueRequest, Long> {

    // 등록 Facade가 트랜잭션 밖에서 부른다. open-in-view가 꺼져 있어 USED 요청의 키를 지연 로딩하면 실패하므로 함께 읽는다.
    // 잠그지 않는다 — 요청은 한 회원 소유이고 그 회원의 등록 경로는 모두 MEMBER 잠금으로 직렬화된다
    @Query("SELECT r FROM BillingKeyIssueRequest r LEFT JOIN FETCH r.billingKey WHERE r.issueId = :issueId")
    Optional<BillingKeyIssueRequest> findByIssueId(@Param("issueId") String issueId);
}
