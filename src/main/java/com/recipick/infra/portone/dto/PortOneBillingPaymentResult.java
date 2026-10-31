package com.recipick.infra.portone.dto;

import com.recipick.global.common.TimeConstants;
import java.time.LocalDateTime;
import java.util.Set;

// 빌링키 청구 호출 결과. 호출자(실행기)는 outcome을 판정에 쓰지 않고 같은 결제 ID를 재조회해 확정한다 — 이 값은 로그·운영 참고용이다.
// detail에는 허용 목록(SAFE_DETAILS)의 고정 코드만 들어간다. PG 오류 본문의 message·pgMessage·pgCode는 자유 형식 외부 문자열이라
// 읽지도 저장하지도 않는다
public record PortOneBillingPaymentResult(
    PortOneBillingPaymentOutcome outcome,
    String pgTxId,
    LocalDateTime paidAt,
    String detail
) {

    public static final String DETAIL_OTHER = "OTHER";
    public static final String DETAIL_MISSING_FIELD = "MISSING_FIELD";
    public static final String DETAIL_HTTP_5XX = "HTTP_5XX";
    public static final String DETAIL_TIMEOUT = "TIMEOUT";
    public static final String DETAIL_CONNECTION = "CONNECTION";
    public static final String DETAIL_PARSE = "PARSE";

    // detail·failureReason에 남겨도 되는 코드. PG 오류 type은 여기 있을 때만 그대로 쓰고, 없으면 OTHER
    public static final Set<String> SAFE_DETAILS = Set.of(
        "ALREADY_PAID",
        "BILLING_KEY_NOT_FOUND",
        "BILLING_KEY_ALREADY_DELETED",
        "CHANNEL_NOT_FOUND",
        "INVALID_REQUEST",
        "UNAUTHORIZED",
        "FORBIDDEN",
        "PG_PROVIDER",
        DETAIL_HTTP_5XX,
        DETAIL_TIMEOUT,
        DETAIL_CONNECTION,
        DETAIL_PARSE,
        DETAIL_MISSING_FIELD,
        DETAIL_OTHER
    );

    private static final String TYPE_ALREADY_PAID = "ALREADY_PAID";
    private static final String TYPE_BILLING_KEY_NOT_FOUND = "BILLING_KEY_NOT_FOUND";
    private static final String TYPE_BILLING_KEY_ALREADY_DELETED = "BILLING_KEY_ALREADY_DELETED";
    private static final String TYPE_CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND";

    // 200. pgTxId·paidAt이 모두 있어야 성공 후보다. 하나라도 없으면 결제됐는지 알 수 없다(재조회가 정한다)
    public static PortOneBillingPaymentResult from(PortOneBillingPaymentResponse response) {
        if (response == null || response.payment() == null) {
            return unknown(DETAIL_MISSING_FIELD);
        }
        PortOneBillingPaymentResponse.Payment payment = response.payment();
        if (payment.pgTxId() == null || payment.pgTxId().isBlank() || payment.paidAt() == null) {
            return unknown(DETAIL_MISSING_FIELD);
        }
        return new PortOneBillingPaymentResult(PortOneBillingPaymentOutcome.PAID, payment.pgTxId(),
            payment.paidAt().atZoneSameInstant(TimeConstants.ZONE_ID).toLocalDateTime(), null);
    }

    // 4xx·5xx. 2단계 4-3 분류표 그대로. errorType은 오류 본문을 못 읽었으면 null
    public static PortOneBillingPaymentResult fromError(int httpStatus, String errorType) {
        return new PortOneBillingPaymentResult(classify(httpStatus, errorType), null, null,
            safeDetail(httpStatus, errorType));
    }

    public static PortOneBillingPaymentResult unknown(String detail) {
        return new PortOneBillingPaymentResult(PortOneBillingPaymentOutcome.UNKNOWN, null, null, detail);
    }

    private static PortOneBillingPaymentOutcome classify(int httpStatus, String errorType) {
        if (httpStatus == 409 && TYPE_ALREADY_PAID.equals(errorType)) {
            return PortOneBillingPaymentOutcome.ALREADY_PAID;
        }
        if ((httpStatus == 404 && TYPE_BILLING_KEY_NOT_FOUND.equals(errorType))
            || (httpStatus == 409 && TYPE_BILLING_KEY_ALREADY_DELETED.equals(errorType))) {
            return PortOneBillingPaymentOutcome.BILLING_KEY_UNUSABLE;
        }
        if (httpStatus == 400 || httpStatus == 401 || httpStatus == 403) {
            return PortOneBillingPaymentOutcome.REJECTED;
        }
        if (httpStatus == 404 && TYPE_CHANNEL_NOT_FOUND.equals(errorType)) {
            return PortOneBillingPaymentOutcome.REJECTED;
        }
        if (httpStatus == 409) {
            // ALREADY_PAID·BILLING_KEY_ALREADY_DELETED 외의 409. 요청이 거절됐다(설정·상태 문제)
            return PortOneBillingPaymentOutcome.REJECTED;
        }
        // 그 밖의 404(type 없음·미지 type)와 5xx: PortOne 쪽에서 무슨 일이 있었는지 모른다
        return PortOneBillingPaymentOutcome.UNKNOWN;
    }

    // 허용 목록에 있는 type만 그대로. 없거나 못 읽었으면 상태 코드 계열로만 남긴다
    private static String safeDetail(int httpStatus, String errorType) {
        if (errorType != null && SAFE_DETAILS.contains(errorType)) {
            return errorType;
        }
        return httpStatus >= 500 ? DETAIL_HTTP_5XX : DETAIL_OTHER;
    }
}
