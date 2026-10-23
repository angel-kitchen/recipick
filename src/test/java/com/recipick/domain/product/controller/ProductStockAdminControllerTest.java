package com.recipick.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class ProductStockAdminControllerTest extends AdminApiTestSupport {

    private Mart martA;
    private Mart martB;
    private Product product;
    private String url;

    @BeforeEach
    void setUp() {
        martA = saveMart(923L, "재고 마트A");
        martB = saveMart(924L, "재고 마트B");
        product = saveProduct(martA, saveCategory("재고채소"), "애호박", 50);
        url = "/api/admin/products/" + product.getId() + "/stock-adjustments";
    }

    private Map<String, Object> adjustBody(String type, int quantity) {
        return Map.of("type", type, "quantity", quantity, "reason", "사유");
    }

    @Test
    @DisplayName("재고를 늘리면 201과 조정 전·후 수량을 돌려준다")
    void increase() throws Exception {
        // when
        MockHttpServletResponse response = post(url, superToken(), adjustBody("INCREASE", 30));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(body(response).path("message").asText()).isEqualTo("재고가 조정되었습니다.");
        assertThat(data(response).path("beforeQuantity").asInt()).isEqualTo(50);
        assertThat(data(response).path("afterQuantity").asInt()).isEqualTo(80);
        assertThat(product.getTotalQuantity()).isEqualTo(80);
    }

    @Test
    @DisplayName("현재 재고보다 많이 줄이면 400 STOCK_ADJUST_EXCEEDS_AVAILABLE")
    void decreaseExceeds() throws Exception {
        // when
        MockHttpServletResponse response = post(url, superToken(), adjustBody("DECREASE", 51));

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("STOCK-002");
        assertThat(product.getTotalQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("수량이 0이면 400 INVALID_ADJUST_QUANTITY")
    void zeroQuantity() throws Exception {
        // when
        MockHttpServletResponse response = post(url, superToken(), adjustBody("INCREASE", 0));

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("STOCK-003");
    }

    @Test
    @DisplayName("사유가 없으면 400 INVALID_INPUT")
    void missingReason() throws Exception {
        // when
        MockHttpServletResponse response =
            post(url, superToken(), Map.of("type", "INCREASE", "quantity", 5));

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("COMMON-001");
    }

    @Test
    @DisplayName("조정 이력은 최신순으로 조회되고 조정한 관리자 id가 기록된다")
    void history() throws Exception {
        // given
        post(url, martAdminToken(martA.getId()), adjustBody("INCREASE", 10));
        post(url, martAdminToken(martA.getId()),
            Map.of("type", "DECREASE", "quantity", 3, "reason", "폐기"));

        // when
        MockHttpServletResponse response = get(url, martAdminToken(martA.getId()));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("totalElements").asLong()).isEqualTo(2);
        assertThat(data(response).path("content").get(0).path("type").asText()).isEqualTo("DECREASE");
        assertThat(data(response).path("content").get(0).path("reason").asText()).isEqualTo("폐기");
        assertThat(data(response).path("content").get(0).path("adminId").asLong()).isEqualTo(MART_ADMIN_ID);
        assertThat(data(response).path("content").get(1).path("type").asText()).isEqualTo("INCREASE");
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트 상품의 재고를 조정하거나 이력을 조회하면 403 MART_ACCESS_DENIED")
    void otherMart() throws Exception {
        // when
        MockHttpServletResponse adjust = post(url, martAdminToken(martB.getId()), adjustBody("INCREASE", 5));
        MockHttpServletResponse history = get(url, martAdminToken(martB.getId()));

        // then
        assertThat(adjust.getStatus()).isEqualTo(403);
        assertThat(code(adjust)).isEqualTo("AUTH-005");
        assertThat(history.getStatus()).isEqualTo(403);
        assertThat(code(history)).isEqualTo("AUTH-005");
        assertThat(product.getTotalQuantity()).isEqualTo(50);
    }
}
