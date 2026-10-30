package com.recipick.domain.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.order.dto.request.OrderCreateRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCreateRequestTest {

    // 포인트 도입 전 코드가 아래 요청(장바구니 1, 101호)에 대해 만들던 해시. 0P 요청은 이 값을 그대로 내야 한다
    private static final String LEGACY_HASH = "04821a9cf5546644724806a5203fc8951c6974987b6b203d78ec5a0d51c7bb19";

    private OrderCreateRequest request(List<Long> cartItemIds, String detailAddress) {
        return new OrderCreateRequest(cartItemIds, "홍길동", "01012345678", "서울 종로구 삼청로 1",
            detailAddress, "1111014000");
    }

    private OrderCreateRequest requestWithPoint(long usedPoint) {
        return new OrderCreateRequest(List.of(1L), "홍길동", "01012345678", "서울 종로구 삼청로 1",
            "101호", "1111014000", usedPoint);
    }

    @Test
    @DisplayName("요청 해시는 SHA-256 hex 64자이고 장바구니 항목 순서가 달라도 같다")
    void requestHash_ignoresCartItemOrder() {
        // when
        String hash = request(List.of(3L, 1L, 2L), "101호").requestHash();
        String reordered = request(List.of(1L, 2L, 3L), "101호").requestHash();

        // then
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(reordered).isEqualTo(hash);
    }

    @Test
    @DisplayName("배송 정보가 하나라도 다르면 요청 해시가 달라진다")
    void requestHash_changesWithDeliveryInformation() {
        // when
        String hash = request(List.of(1L), "101호").requestHash();
        String changed = request(List.of(1L), "102호").requestHash();

        // then
        assertThat(changed).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("필드 경계가 달라 이어 붙인 문자열이 같아지는 요청도 해시가 다르다")
    void requestHash_distinguishesFieldBoundaries() {
        // given
        OrderCreateRequest first = new OrderCreateRequest(List.of(1L), "홍길동", "01012345678", "주소A",
            "B", "1111014000");
        OrderCreateRequest second = new OrderCreateRequest(List.of(1L), "홍길동", "01012345678", "주소",
            "AB", "1111014000");

        // when, then
        assertThat(first.requestHash()).isNotEqualTo(second.requestHash());
    }

    @Test
    @DisplayName("사용 포인트를 보내지 않은 요청과 0P 요청은 같은 요청이고 포인트 도입 전과 같은 해시를 낸다")
    void requestHash_keepsLegacyHashWithoutPoint() {
        // when
        String omitted = request(List.of(1L), "101호").requestHash();
        String zero = requestWithPoint(0L).requestHash();

        // then
        assertThat(zero).isEqualTo(omitted);
        assertThat(zero).isEqualTo(LEGACY_HASH);
    }

    @Test
    @DisplayName("사용 포인트가 다르면 나머지가 같아도 요청 해시가 달라진다")
    void requestHash_changesWithUsedPoint() {
        // when
        String zero = requestWithPoint(0L).requestHash();
        String threeThousand = requestWithPoint(3_000L).requestHash();
        String fourThousand = requestWithPoint(4_000L).requestHash();

        // then
        assertThat(threeThousand).isNotEqualTo(zero);
        assertThat(fourThousand).isNotEqualTo(threeThousand);
    }
}
