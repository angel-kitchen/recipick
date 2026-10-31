package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.BillingKeyIssueRequest;
import com.recipick.domain.payment.repository.BillingKeyIssueRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 일회성 카드 등록 요청(issueId)의 발급과 조회. 등록 판정은 BillingKeyService가 한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingKeyIssueRequestService {

    private static final String ISSUE_ID_PREFIX = "rcp-bk-";

    private final BillingKeyIssueRequestRepository billingKeyIssueRequestRepository;
    private final Clock clock;

    // 토큰은 무작위 UUID라 추측할 수 없다. 회원 잠금은 걸지 않는다 — 요청 행은 서로 독립이다
    @Transactional
    public BillingKeyIssueRequest create(Long memberId) {
        return billingKeyIssueRequestRepository.save(BillingKeyIssueRequest.requested(
            memberId, ISSUE_ID_PREFIX + UUID.randomUUID(), LocalDateTime.now(clock)));
    }

    // 등록 Facade의 1차 확인(트랜잭션 밖). 묶인 키까지 함께 읽는다
    public Optional<BillingKeyIssueRequest> findByIssueId(String issueId) {
        return billingKeyIssueRequestRepository.findByIssueId(issueId);
    }
}
