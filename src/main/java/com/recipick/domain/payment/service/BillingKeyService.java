package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.BillingKeyIssueRequest;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.BillingKeyIssueRequestRepository;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 빌링키 등록·교체 판정과 활성 빌링키 확인(C-10). PG 검증은 Facade가 트랜잭션 밖에서 끝내고, 여기는 회원 잠금 아래의
// DB 판정만 한다. 잠그는 행은 존재하는 행뿐이다 — ID를 일반 조회로 읽고 있으면 PK로 잠근다(설계 5-3·5-4)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingKeyService {

    // 엔티티 @UniqueConstraint 이름. 운영 DDL이 기존 인덱스를 이 이름으로 바꾼다
    static final String BILLING_KEY_UNIQUE_CONSTRAINT = "uk_billing_key_portone_billing_key";

    private final BillingKeyRepository billingKeyRepository;
    private final BillingKeyIssueRequestRepository billingKeyIssueRequestRepository;
    private final Clock clock;

    // C-10. 회원 탈퇴 Facade가 회원 잠금 뒤 부른다(0단계 계약 시그니처)
    public boolean hasActiveBillingKey(Long memberId) {
        return billingKeyRepository.existsByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE);
    }

    // GET /api/billing-keys/me
    public Optional<BillingKeySummary> findActive(Long memberId) {
        return billingKeyRepository.findByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE)
            .map(BillingKeySummary::from);
    }

    // 회원 잠금 트랜잭션(Processor) 안에서만 부른다. 순서: 발급 요청 재검사 → 같은 키 행 → 현재 ACTIVE 행 → 분기(설계 5-5).
    // 발급 요청을 트랜잭션 밖(Facade)에서 한 번 확인했어도 여기서 다시 본다 — 같은 회원이 같은 토큰으로 키 두 개를 동시에
    // 보내면 회원 잠금이 직렬화하고, 늦은 쪽이 여기서 "다른 키에 사용됨"을 본다
    @Transactional(propagation = Propagation.MANDATORY)
    public BillingKeyRegistration register(Long memberId, Long issueRequestId, String portoneBillingKey,
                                           LocalDateTime issuedAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 회원 잠금 뒤 첫 일반 읽기라 최신이다
        BillingKeyIssueRequest issueRequest = billingKeyIssueRequestRepository.findById(issueRequestId)
            .filter(request -> request.isOwnedBy(memberId))
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.BILLING_KEY_ISSUE_REQUEST_INVALID));

        // (a) 같은 키 행 — ID만 읽고, 있으면 PK로 잠그며 다시 읽는다. 없으면 아무것도 잠그지 않는다
        Optional<BillingKey> sameKey = billingKeyRepository.findIdByPortoneBillingKey(portoneBillingKey)
            .flatMap(billingKeyRepository::findByIdForUpdate);
        requireIssueRequestUsable(issueRequest, portoneBillingKey, sameKey, now);
        if (sameKey.isPresent() && !sameKey.get().getMemberId().equals(memberId)) {
            throw new BusinessException(PaymentErrorCode.BILLING_KEY_ALREADY_USED);
        }

        // (b) 현재 ACTIVE 행 — (a)의 행과 같으면 다시 잠그지 않는다. 첫 등록 회원은 empty라 갭 잠금이 없다
        Optional<BillingKey> active = billingKeyRepository.findIdByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE)
            .filter(activeId -> sameKey.map(key -> !key.getId().equals(activeId)).orElse(true))
            .flatMap(billingKeyRepository::findByIdForUpdate);

        // (c) 분기표
        if (sameKey.isEmpty()) {
            active.ifPresent(BillingKey::replace);
            BillingKey saved = saveNew(memberId, portoneBillingKey, issuedAt);
            issueRequest.markUsed(saved, now);
            return new BillingKeyRegistration(saved.getId(), active.isPresent()
                ? BillingKeyRegistrationOutcome.REPLACED
                : BillingKeyRegistrationOutcome.REGISTERED);
        }
        BillingKey mine = sameKey.get();
        return switch (mine.getStatus()) {
            case ACTIVE -> {
                bindIfRequested(issueRequest, mine, now);
                yield new BillingKeyRegistration(mine.getId(), BillingKeyRegistrationOutcome.ALREADY_ACTIVE);
            }
            case REPLACED -> {
                active.ifPresent(BillingKey::replace);
                mine.reactivate();
                bindIfRequested(issueRequest, mine, now);
                yield new BillingKeyRegistration(mine.getId(), BillingKeyRegistrationOutcome.REACTIVATED);
            }
            case REVOKED, DELETE_REQUESTED, DELETED ->
                throw new BusinessException(PaymentErrorCode.BILLING_KEY_UNUSABLE);
        };
    }

    // 설계 5-5 ⑧-1. USED 요청인데 묶인 키 행이 없으면 데이터 불일치라 500으로 드러낸다
    private static void requireIssueRequestUsable(BillingKeyIssueRequest issueRequest, String portoneBillingKey,
                                                  Optional<BillingKey> sameKey, LocalDateTime now) {
        if (!issueRequest.allowsSubmissionOf(portoneBillingKey, now)) {
            throw new BusinessException(PaymentErrorCode.BILLING_KEY_ISSUE_REQUEST_INVALID);
        }
        if (issueRequest.isUsed() && sameKey.isEmpty()) {
            throw new IllegalStateException("사용된 카드 등록 요청에 묶인 빌링키 행이 없습니다: " + issueRequest);
        }
    }

    // REQUESTED 요청은 첫 사용에서 키에 묶인다. USED·같은 키 재제출은 바꾸지 않는다
    private static void bindIfRequested(BillingKeyIssueRequest issueRequest, BillingKey key, LocalDateTime now) {
        if (!issueRequest.isUsed()) {
            issueRequest.markUsed(key, now);
        }
    }

    // 두 회원이 같은 키를 동시에 등록하면 서로 다른 MEMBER를 잠가 둘 다 여기 오고, UNIQUE가 한 명만 통과시킨다.
    // 즉시 flush해 충돌을 여기서 받는다(기존 requestMemberRefund·SignupService와 같은 방식).
    // 빌링키 UNIQUE(uk_billing_key_portone_billing_key) 위반만 BILLING-002로 바꾸고 다른 무결성 오류는 그대로 올린다.
    // 원인 예외의 메시지에는 중복 값(키 원문)이 있어 cause로 넘기지 않고, 로그에는 제약 이름만 남긴다
    private BillingKey saveNew(Long memberId, String portoneBillingKey, LocalDateTime issuedAt) {
        try {
            return billingKeyRepository.saveAndFlush(BillingKey.issued(memberId, portoneBillingKey, issuedAt));
        } catch (DataIntegrityViolationException e) {
            String constraint = violatedConstraintNameOf(e);
            if (constraint == null || !constraint.contains(BILLING_KEY_UNIQUE_CONSTRAINT)) {
                throw e;
            }
            log.warn("빌링키 등록 거절: UNIQUE 충돌, memberId={}, constraint={}", memberId, constraint);
            throw new BusinessException(PaymentErrorCode.BILLING_KEY_ALREADY_USED);
        }
    }

    private static String violatedConstraintNameOf(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
