package com.recipick.infra.portone.dto;

// 정합화용 빌링키 조회 결과. 예외 대신 분류로 돌려준다. 폐기 근거가 되는 것은 PortOne이 "삭제됨"(DELETED)이라고 답했거나
// HTTP 404이면서 오류 type이 BILLING_KEY_NOT_FOUND인 경우뿐이다. 그 밖의 404·인증 오류·5xx·timeout·해석 실패는
// 키 상태를 알 수 없음(UNAVAILABLE)이다. code는 고정 코드만 담는다
public record PortOneBillingKeyLookup(Kind kind, PortOneBillingKeyInfo info, String code) {

    public static final String CODE_NOT_FOUND = "BILLING_KEY_NOT_FOUND";

    public enum Kind { FOUND, NOT_FOUND, UNAVAILABLE }

    public static PortOneBillingKeyLookup found(PortOneBillingKeyInfo info) {
        return new PortOneBillingKeyLookup(Kind.FOUND, info, null);
    }

    public static PortOneBillingKeyLookup notFound() {
        return new PortOneBillingKeyLookup(Kind.NOT_FOUND, null, CODE_NOT_FOUND);
    }

    public static PortOneBillingKeyLookup unavailable(String code) {
        return new PortOneBillingKeyLookup(Kind.UNAVAILABLE, null, code);
    }

    public boolean confirmsRevocation() {
        return kind == Kind.NOT_FOUND
            || (kind == Kind.FOUND && info != null && info.status() == PortOneBillingKeyStatus.DELETED);
    }
}
