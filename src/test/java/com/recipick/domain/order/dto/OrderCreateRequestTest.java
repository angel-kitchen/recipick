package com.recipick.domain.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.order.dto.request.OrderCreateRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCreateRequestTest {

    private OrderCreateRequest request(List<Long> cartItemIds, String detailAddress) {
        return new OrderCreateRequest(cartItemIds, "홍길동", "01012345678", "서울 종로구 삼청로 1",
                detailAddress, "1111014000");
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
}
