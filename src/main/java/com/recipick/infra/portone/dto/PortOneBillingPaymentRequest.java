package com.recipick.infra.portone.dto;

// POST /payments/{paymentId}/billing-key 요청 본문(PortOne V2). 빌링키·고객 정보가 들어 있어 toString은 전부 가린다 —
// 이 객체를 로그·예외 메시지에 넣어도 원문이 새지 않는다.
// 빌링키·주문명·고객 정보·금액·통화 + KG이니시스 V2 필수 productCount=1.
// 필드 지원·필수 여부는 테스트 채널 실연동으로 확인한다
public record PortOneBillingPaymentRequest(
    String billingKey,
    String orderName,
    Customer customer,
    Amount amount,
    String currency,
    Integer productCount
) {

    public static final String ORDER_NAME = "레시픽 멤버십 월 이용료";
    public static final String CURRENCY = "KRW";
    // KG이니시스 V2 빌링키 결제 필수. 멤버십은 상품 한 건이라 항상 1
    public static final int PRODUCT_COUNT = 1;

    // id는 2a 카드 등록의 customerId와 같은 값(String.valueOf(memberId))이라 PortOne 쪽 고객이 하나로 묶인다
    public record Customer(String id, Name name, String email, String phoneNumber) {

        @Override
        public String toString() {
            return "Customer[REDACTED]";
        }
    }

    public record Name(String full) {

        @Override
        public String toString() {
            return "Name[REDACTED]";
        }
    }

    public record Amount(long total) {
    }

    public static PortOneBillingPaymentRequest of(String billingKey, long total, String customerId, String name,
                                                  String email, String phone) {
        return new PortOneBillingPaymentRequest(
            billingKey,
            ORDER_NAME,
            new Customer(customerId, new Name(name), email, phone),
            new Amount(total),
            CURRENCY,
            PRODUCT_COUNT
        );
    }

    @Override
    public String toString() {
        return "PortOneBillingPaymentRequest[REDACTED]";
    }
}
