package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.WebhookEventService;
import com.recipick.domain.payment.service.WebhookReceiveOutcome;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.PortOneProperties;
import com.recipick.infra.portone.PortOneWebhookParser;
import com.recipick.infra.portone.PortOneWebhookVerification;
import com.recipick.infra.portone.PortOneWebhookVerifier;
import com.recipick.infra.portone.dto.PortOneWebhook;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import com.recipick.infra.portone.dto.PortOneWebhookType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// PortOne 웹훅 수신(inbox)의 진입점. 요청 스레드는 검증 → 저장 → 200까지만 하고 결제·환불 반영은 지연 큐 Worker가 한다.
// PortOne은 30초 안에 응답이 없으면 재전송하므로 여기서 PortOne 재조회나 잠금을 잡지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneWebhookFacade {

    private final PortOneWebhookVerifier portOneWebhookVerifier;
    private final PortOneWebhookParser portOneWebhookParser;
    private final PortOneProperties portOneProperties;
    private final WebhookEventService webhookEventService;
    private final Clock clock;

    public WebhookReceiveOutcome receive(
        String webhookId, String webhookTimestamp, String webhookSignature, String body) {
        PortOneWebhookVerification verification =
            portOneWebhookVerifier.verify(webhookId, webhookTimestamp, webhookSignature, body);
        if (verification == PortOneWebhookVerification.NOT_CONFIGURED) {
            // 우리 설정 문제다. 500이면 PortOne이 다시 보내 주므로 설정을 고친 뒤 받을 수 있다
            log.error("PortOne 웹훅 시크릿이 설정되지 않아 웹훅을 받을 수 없습니다. webhookId={}", webhookId);
            throw new BusinessException(PaymentErrorCode.WEBHOOK_NOT_CONFIGURED);
        }
        if (!verification.isValid()) {
            // 위조·재생일 수 있어 DB에 아무것도 남기지 않는다. 팀 공통 코드 AUTH-010(401) — 웹훅은 JWT 대신 서명이 인증이다
            log.warn("PortOne 웹훅 검증 실패: result={}, webhookId={}", verification, webhookId);
            throw new BusinessException(AuthErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        Optional<PortOneWebhook> parsed = portOneWebhookParser.parse(body);
        if (parsed.isEmpty()) {
            // 서명은 맞는데 JSON이 아니다. 인증 실패가 아니라 형식 오류라 COMMON-002(400)
            log.warn("PortOne 웹훅 본문을 읽을 수 없습니다. webhookId={}", webhookId);
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST_FORMAT);
        }

        WebhookEvent saved;
        try {
            saved = webhookEventService.save(toEvent(webhookId, parsed.get(), body));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateWebhookId(e)) {
                log.info("이미 받은 PortOne 웹훅 재전송: webhookId={}", webhookId);
                return WebhookReceiveOutcome.DUPLICATE;
            }
            throw e;
        }
        if (saved.getStatus() == WebhookEventStatus.IGNORED) {
            return WebhookReceiveOutcome.IGNORED;
        }
        // 4단계: 여기서 지연 큐에 WEBHOOK_PROCESS:{saved.getId()}를 등록한다. 등록 실패는 ERROR 로그 + 200 (DB가 원본)
        return WebhookReceiveOutcome.RECEIVED;
    }

    // 처리 대상이 아닌 웹훅도 저장한다. 저장해야 같은 webhook_id 재전송이 UNIQUE에 걸려 조회 없이 200으로 끝난다
    private WebhookEvent toEvent(String webhookId, PortOneWebhook webhook, String body) {
        String type = webhook.type();
        PortOneWebhook.Data data = webhook.data();
        String paymentId = data == null ? null : data.paymentId();
        String storeId = data == null ? null : data.storeId();
        LocalDateTime now = LocalDateTime.now(clock);

        if (PortOneWebhookType.of(type).isEmpty()) {
            return WebhookEvent.ignored(webhookId, type, paymentId, body, "처리하지 않는 type: " + type, now);
        }
        // 다른 상점·환경의 웹훅이 섞여 들어오는 사고를 막는다. 비교 기준이 없으면 통과시키지 않고(fail-closed) 500으로
        // 거절한다 — 앱은 뜨되 처리 대상 웹훅만 막히고, PortOne 재전송 덕에 설정 뒤 다시 받을 수 있다
        String configuredStoreId = portOneProperties.storeId();
        if (!StringUtils.hasText(configuredStoreId)) {
            log.error("PortOne storeId가 설정되지 않아 웹훅을 처리할 수 없습니다. webhookId={}, type={}", webhookId, type);
            throw new BusinessException(PaymentErrorCode.WEBHOOK_NOT_CONFIGURED);
        }
        if (!configuredStoreId.equals(storeId)) {
            log.warn("다른 storeId의 PortOne 웹훅: webhookId={}, type={}, storeId={}", webhookId, type, storeId);
            return WebhookEvent.ignored(webhookId, type, paymentId, body, "storeId 불일치: " + storeId, now);
        }
        if (!StringUtils.hasText(paymentId)) {
            log.warn("paymentId 없는 PortOne 웹훅: webhookId={}, type={}", webhookId, type);
            return WebhookEvent.ignored(webhookId, type, paymentId, body, "paymentId 없음", now);
        }
        return WebhookEvent.received(webhookId, type, paymentId, body);
    }

    // Hibernate가 뽑아 둔 제약 이름으로 판정한다. 메시지 문자열은 드라이버·버전에 따라 바뀌지만 제약 이름은 우리가 정했다.
    // MySQL은 "webhook_event.uk_…"처럼 테이블명을 앞에 붙이므로 contains로 본다
    private static boolean isDuplicateWebhookId(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName().toLowerCase(Locale.ROOT)
                    .contains(WebhookEvent.WEBHOOK_ID_UNIQUE_CONSTRAINT);
            }
        }
        return false;
    }
}
