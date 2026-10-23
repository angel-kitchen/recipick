package com.recipick.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class ProductAdminControllerTest extends AdminApiTestSupport {

    private Mart martA;
    private Mart martB;
    private Category category;
    private Product productA1;

    @BeforeEach
    void setUp() {
        martA = saveMart(921L, "컨트롤러 마트A");
        martB = saveMart(922L, "컨트롤러 마트B");
        category = saveCategory("컨트롤러채소");
        productA1 = saveProduct(martA, category, "애호박", 10);
        saveProduct(martA, category, "청양고추", 10);
        saveProduct(martB, category, "삼겹살", 10);
    }

    private Map<String, Object> createBody(Long martId, String name) {
        return Map.of(
            "martId", martId,
            "categoryId", category.getId(),
            "name", name,
            "saleUnitAmount", "300.00",
            "saleUnit", "G",
            "price", 1500);
    }

    @Test
    @DisplayName("토큰이 없으면 401 LOGIN_REQUIRED")
    void noToken() throws Exception {
        // when
        MockHttpServletResponse response = get("/api/admin/products", null);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(code(response)).isEqualTo("AUTH-001");
    }

    @Test
    @DisplayName("회원 토큰이면 403 ACCESS_DENIED")
    void memberTokenDenied() throws Exception {
        // when
        MockHttpServletResponse response = get("/api/admin/products", memberToken());

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-004");
    }

    @Test
    @DisplayName("SUPER_ADMIN은 martId로 마트를 지정해 상품 목록을 조회한다")
    void listBySuperAdmin() throws Exception {
        // when
        MockHttpServletResponse response =
            get("/api/admin/products?martId=" + martA.getId(), superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).path("message").asText()).isEqualTo("조회되었습니다.");
        assertThat(data(response).path("content").size()).isEqualTo(2);
        assertThat(data(response).path("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("MART_ADMIN은 martId를 생략해도 본인 마트 상품만 조회한다")
    void listByMartAdmin() throws Exception {
        // when
        MockHttpServletResponse response = get("/api/admin/products", martAdminToken(martA.getId()));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("content").size()).isEqualTo(2);
        data(response).path("content").forEach(item ->
            assertThat(item.path("martId").asLong()).isEqualTo(martA.getId()));
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트를 지정해 조회하면 403 MART_ACCESS_DENIED")
    void listOtherMart() throws Exception {
        // when
        MockHttpServletResponse response =
            get("/api/admin/products?martId=" + martB.getId(), martAdminToken(martA.getId()));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-005");
    }

    @Test
    @DisplayName("상품을 등록하면 201과 productId를 돌려주고 판매 중·재고 0으로 저장된다")
    void create() throws Exception {
        // given
        Map<String, Object> request = createBody(martA.getId(), "새 상품");

        // when
        MockHttpServletResponse response = post("/api/admin/products", superToken(), request);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(body(response).path("message").asText()).isEqualTo("등록되었습니다.");
        Long productId = data(response).path("productId").asLong();
        Product saved = productRepository.findById(productId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.SELLING);
        assertThat(saved.getTotalQuantity()).isZero();
    }

    @Test
    @DisplayName("상품명이 비어 있으면 400 INVALID_INPUT")
    void createInvalid() throws Exception {
        // given
        Map<String, Object> request = createBody(martA.getId(), "");

        // when
        MockHttpServletResponse response = post("/api/admin/products", superToken(), request);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("COMMON-001");
    }

    @Test
    @DisplayName("같은 마트에 같은 이름·브랜드 상품을 또 등록하면 409 PRODUCT_NAME_DUPLICATED")
    void createDuplicated() throws Exception {
        // given
        Map<String, Object> request = createBody(martA.getId(), "애호박");

        // when
        MockHttpServletResponse response = post("/api/admin/products", superToken(), request);

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("PRODUCT-008");
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트에 등록하면 403 MART_ACCESS_DENIED")
    void createOtherMart() throws Exception {
        // given
        Map<String, Object> request = createBody(martB.getId(), "새 상품");

        // when
        MockHttpServletResponse response =
            post("/api/admin/products", martAdminToken(martA.getId()), request);

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-005");
    }

    @Test
    @DisplayName("판매 상태를 바꾸면 200과 바뀐 상태를 돌려준다")
    void changeStatus() throws Exception {
        // when
        MockHttpServletResponse response = patch(
            "/api/admin/products/" + productA1.getId() + "/status",
            martAdminToken(martA.getId()), Map.of("status", "STOPPED"));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("status").asText()).isEqualTo("STOPPED");
        assertThat(productA1.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("허용되지 않는 판매 상태 값이면 400 COMMON-002")
    void changeStatusInvalid() throws Exception {
        // when
        MockHttpServletResponse response = patch(
            "/api/admin/products/" + productA1.getId() + "/status",
            superToken(), Map.of("status", "UNKNOWN"));

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("COMMON-002");
    }

    @Test
    @DisplayName("상품을 삭제하면 200이고 다시 삭제하면 404 PRODUCT_NOT_FOUND")
    void deleteProduct() throws Exception {
        // when
        MockHttpServletResponse first =
            delete("/api/admin/products/" + productA1.getId(), martAdminToken(martA.getId()));
        MockHttpServletResponse second =
            delete("/api/admin/products/" + productA1.getId(), martAdminToken(martA.getId()));

        // then
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(body(first).path("message").asText()).isEqualTo("삭제되었습니다.");
        assertThat(productA1.isDeleted()).isTrue();
        assertThat(second.getStatus()).isEqualTo(404);
        assertThat(code(second)).isEqualTo("PRODUCT-001");
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트 상품을 삭제하면 403 MART_ACCESS_DENIED")
    void deleteOtherMart() throws Exception {
        // when
        MockHttpServletResponse response =
            delete("/api/admin/products/" + productA1.getId(), martAdminToken(martB.getId()));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-005");
        assertThat(productA1.isDeleted()).isFalse();
    }
}
