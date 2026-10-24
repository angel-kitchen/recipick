package com.recipick.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

class ProductControllerTest extends AdminApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    private Mart martA;
    private Mart martB;
    private Category vegetable;
    private Category meat;
    private Product zucchini;
    private Product otherMartProduct;

    @BeforeEach
    void setUp() {
        martA = saveMart(931L, "회원 마트A");
        martB = saveMart(932L, "회원 마트B");
        vegetable = saveCategory("회원채소");
        meat = saveCategory("회원고기");
        zucchini = saveProduct(martA, vegetable, "애호박", 1000L, 10, false);
        saveProduct(martA, vegetable, "청양고추", 3000L, 0, false);
        saveProduct(martA, meat, "삼겹살", 2000L, 10, false);
        saveProduct(martA, vegetable, "삭제된 양파", 500L, 10, true);
        otherMartProduct = saveProduct(martB, vegetable, "다른마트 애호박", 1000L, 10, false);
    }

    private Product saveProduct(Mart mart, Category category, String name, Long price, int quantity, boolean deleted) {
        return productRepository.save(Product.builder()
            .mart(mart).category(category).name(name).price(price)
            .totalQuantity(quantity).deleted(deleted).build());
    }

    private String memberToken(Long martId) {
        Member member = memberRepository.saveAndFlush(Member.builder()
            .email(UUID.randomUUID() + "@product.test").name("상품 조회")
            .phone(UUID.randomUUID().toString().substring(0, 20))
            .passwordHash("test-password-hash").martId(martId).build());
        return tokens.createMemberTokens(member.getId()).accessToken();
    }

    private List<String> names(MockHttpServletResponse response) throws Exception {
        List<String> names = new ArrayList<>();
        for (JsonNode item : data(response).path("content")) {
            names.add(item.path("name").asText());
        }
        return names;
    }

    @Test
    @DisplayName("토큰이 없으면 401, 관리자 토큰이면 403")
    void authorization() throws Exception {
        // when
        MockHttpServletResponse noToken = get("/api/products", null);
        MockHttpServletResponse admin = get("/api/products", superToken());

        // then
        assertThat(noToken.getStatus()).isEqualTo(401);
        assertThat(code(noToken)).isEqualTo("AUTH-001");
        assertThat(admin.getStatus()).isEqualTo(403);
        assertThat(code(admin)).isEqualTo("AUTH-004");
    }

    @Test
    @DisplayName("이용 마트의 상품만, 삭제된 상품 없이 조회되고 재고 수량은 노출되지 않는다")
    void search() throws Exception {
        // when
        MockHttpServletResponse response = get("/api/products", memberToken(martA.getId()));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).path("message").asText()).isEqualTo("조회되었습니다.");
        assertThat(names(response)).containsExactlyInAnyOrder("애호박", "청양고추", "삼겹살");
        assertThat(data(response).path("content").get(0).has("totalQuantity")).isFalse();
    }

    @Test
    @DisplayName("재고가 0인 상품은 soldOut이 true이다")
    void soldOut() throws Exception {
        // when
        MockHttpServletResponse response = get("/api/products?keyword=고추", memberToken(martA.getId()));

        // then
        assertThat(names(response)).containsExactly("청양고추");
        assertThat(data(response).path("content").get(0).path("soldOut").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("키워드·카테고리·가격 범위로 필터링한다")
    void filters() throws Exception {
        // given
        String token = memberToken(martA.getId());

        // when
        MockHttpServletResponse keyword = get("/api/products?keyword=호박", token);
        MockHttpServletResponse byCategory = get("/api/products?categoryId=" + meat.getId(), token);
        MockHttpServletResponse byPrice = get("/api/products?minPrice=1500&maxPrice=2500", token);
        MockHttpServletResponse unknownCategory = get("/api/products?categoryId=999999", token);

        // then
        assertThat(names(keyword)).containsExactly("애호박");
        assertThat(names(byCategory)).containsExactly("삼겹살");
        assertThat(names(byPrice)).containsExactly("삼겹살");
        assertThat(unknownCategory.getStatus()).isEqualTo(200);
        assertThat(names(unknownCategory)).isEmpty();
    }

    @Test
    @DisplayName("가격 오름차순·내림차순으로 정렬한다")
    void sort() throws Exception {
        // given
        String token = memberToken(martA.getId());

        // when
        MockHttpServletResponse asc = get("/api/products?sort=price,asc", token);
        MockHttpServletResponse desc = get("/api/products?sort=price,desc", token);

        // then
        assertThat(names(asc)).containsExactly("애호박", "삼겹살", "청양고추");
        assertThat(names(desc)).containsExactly("청양고추", "삼겹살", "애호박");
    }

    @Test
    @DisplayName("최소 가격이 최대 가격보다 크거나 허용되지 않는 정렬이면 400 INVALID_INPUT")
    void invalidQuery() throws Exception {
        // given
        String token = memberToken(martA.getId());

        // when
        MockHttpServletResponse range = get("/api/products?minPrice=3000&maxPrice=1000", token);
        MockHttpServletResponse sort = get("/api/products?sort=name,asc", token);

        // then
        assertThat(range.getStatus()).isEqualTo(400);
        assertThat(code(range)).isEqualTo("COMMON-001");
        assertThat(sort.getStatus()).isEqualTo(400);
        assertThat(code(sort)).isEqualTo("COMMON-001");
    }

    @Test
    @DisplayName("이용 마트가 없는 회원은 409 MART_NOT_SELECTED")
    void noMart() throws Exception {
        // given
        String token = memberToken(null);

        // when
        MockHttpServletResponse search = get("/api/products", token);
        MockHttpServletResponse detail = get("/api/products/" + zucchini.getId(), token);

        // then
        assertThat(search.getStatus()).isEqualTo(409);
        assertThat(code(search)).isEqualTo("PRODUCT-010");
        assertThat(detail.getStatus()).isEqualTo(409);
        assertThat(code(detail)).isEqualTo("PRODUCT-010");
    }

    @Test
    @DisplayName("상세 조회는 카테고리명과 품절 여부를 돌려주고 다른 마트 상품은 404")
    void detail() throws Exception {
        // given
        String token = memberToken(martA.getId());

        // when
        MockHttpServletResponse found = get("/api/products/" + zucchini.getId(), token);
        MockHttpServletResponse otherMart = get("/api/products/" + otherMartProduct.getId(), token);

        // then
        assertThat(found.getStatus()).isEqualTo(200);
        assertThat(data(found).path("name").asText()).isEqualTo("애호박");
        assertThat(data(found).path("categoryName").asText()).isEqualTo("회원채소");
        assertThat(data(found).path("soldOut").asBoolean()).isFalse();
        assertThat(otherMart.getStatus()).isEqualTo(404);
        assertThat(code(otherMart)).isEqualTo("PRODUCT-001");
    }
}
