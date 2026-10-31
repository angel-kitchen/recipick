package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.config.BillingKeyProperties;
import com.recipick.domain.payment.dto.request.BillingKeyRegisterRequest;
import com.recipick.domain.payment.dto.response.BillingKeyIssueRequestResponse;
import com.recipick.domain.payment.dto.response.BillingKeyMeResponse;
import com.recipick.domain.payment.dto.response.BillingKeyRegisterResponse;
import com.recipick.domain.payment.entity.BillingKeyIssueRequest;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.BillingKeyIssuanceVerifier;
import com.recipick.domain.payment.service.BillingKeyIssuanceVerifier.RejectReason;
import com.recipick.domain.payment.service.BillingKeyIssueRequestService;
import com.recipick.domain.payment.service.BillingKeyRegistration;
import com.recipick.domain.payment.service.BillingKeyService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.PortOneBillingClient;
import com.recipick.infra.portone.PortOneProperties;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfo;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 카드 등록 흐름. 이 클래스에는 트랜잭션이 없다 — PortOne 조회·검증은 회원 행을 잠그지 않은 채 끝내고,
// DB 변경은 Processor의 회원 잠금 트랜잭션에서만 한다. 로그에는 회원 ID·사유 코드만 남기고 키·토큰 원문은 남기지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingKeyRegistrationFacade {

    private static final String ISSUE_NAME = "레시픽 멤버십 자동결제 카드 등록";
    // 결제창 표시 금액. 실제 청구 금액의 단일 출처는 4단계가 정하고 그때 이 상수를 그쪽으로 옮긴다(설계 5-6)
    private static final long DISPLAY_AMOUNT = 3_900L;
    private static final String CURRENCY = "KRW";
    // 설정·PG 동작 이상을 뜻하는 사유. 운영자가 바로 봐야 해서 ERROR로 남긴다
    private static final Set<RejectReason> OPERATIONAL_REASONS =
        Set.of(RejectReason.LIVE_CHANNEL_BLOCKED, RejectReason.OWNERSHIP_UNVERIFIABLE);

    private final BillingKeyProperties billingKeyProperties;
    private final PortOneProperties portOneProperties;
    private final MemberService memberService;
    private final BillingKeyIssueRequestService billingKeyIssueRequestService;
    private final BillingKeyService billingKeyService;
    private final PortOneBillingClient portOneBillingClient;
    private final BillingKeyIssuanceVerifier verifier;
    private final BillingKeyRegistrationProcessor processor;
    private final Clock clock;

    // POST /api/billing-keys/issue-requests. 회원 존재·탈퇴는 기존 조회로 확인한다(MEMBER-001)
    public BillingKeyIssueRequestResponse issue(Long memberId) {
        requireRegistrationEnabled();
        requireConfig();
        memberService.getProfile(memberId);
        BillingKeyIssueRequest request = billingKeyIssueRequestService.create(memberId);
        return new BillingKeyIssueRequestResponse(
            request.getIssueId(),
            ISSUE_NAME,
            portOneProperties.storeId(),
            portOneProperties.channelKey(),
            String.valueOf(memberId),
            DISPLAY_AMOUNT,
            CURRENCY,
            request.getExpiresAt()
        );
    }

    // POST /api/billing-keys. ① 플래그 → ③ 발급 요청 1차 확인 → ④ 설정 → ⑤ PortOne 조회 → ⑥ 검증 → ⑦ 회원 잠금 TX
    public BillingKeyRegisterResponse register(Long memberId, BillingKeyRegisterRequest request) {
        requireRegistrationEnabled();
        LocalDateTime now = LocalDateTime.now(clock);
        BillingKeyIssueRequest issueRequest = billingKeyIssueRequestService.findByIssueId(request.issueId())
            .filter(found -> found.isOwnedBy(memberId))
            .filter(found -> found.allowsSubmissionOf(request.billingKey(), now))
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.BILLING_KEY_ISSUE_REQUEST_INVALID));
        requireConfig();
        PortOneBillingKeyInfo info = portOneBillingClient.getBillingKey(request.billingKey())
            .orElseThrow(() -> {
                log.warn("빌링키 등록 거절: memberId={}, reason=NOT_FOUND", memberId);
                return new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
            });
        verifier.verify(memberId, request.billingKey(), issueRequest.getIssueId(), info)
            .ifPresent(reason -> {
                if (OPERATIONAL_REASONS.contains(reason)) {
                    log.error("빌링키 등록 거절(설정·PG 동작 확인 필요): memberId={}, reason={}", memberId, reason);
                } else {
                    log.warn("빌링키 등록 거절: memberId={}, reason={}", memberId, reason);
                }
                throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
            });
        BillingKeyRegistration registration =
            processor.register(memberId, issueRequest.getId(), request.billingKey(), info.issuedAt());
        return new BillingKeyRegisterResponse(registration.billingKeyId(), BillingKeyStatus.ACTIVE,
            registration.outcome());
    }

    // GET /api/billing-keys/me. 플래그와 무관하다
    public BillingKeyMeResponse getMine(Long memberId) {
        return billingKeyService.findActive(memberId)
            .map(BillingKeyMeResponse::of)
            .orElseGet(BillingKeyMeResponse::none);
    }

    private void requireRegistrationEnabled() {
        if (!billingKeyProperties.registrationEnabled()) {
            throw new BusinessException(PaymentErrorCode.BILLING_KEY_REGISTRATION_DISABLED);
        }
    }

    // 상점·채널 설정 없이는 검증이 성립하지 않으므로 등록을 열지 않는다
    private void requireConfig() {
        if (!StringUtils.hasText(portOneProperties.storeId()) || !StringUtils.hasText(portOneProperties.channelKey())) {
            log.error("PortOne 상점·채널 설정이 없어 카드 등록을 진행할 수 없음");
            throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }
}
