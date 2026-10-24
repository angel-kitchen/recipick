package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class CartQueryIntegrationTest extends IntegrationTestSupport {

    // data.sql 시연 데이터의 1번·2번 마트
    private static final Long MART_ID = 1L;
    private static final Long OTHER_MART_ID = 2L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MartRepository martRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CartItemRepository cartItemRepository;

    private Member member;
    private Member otherMember;
    private String token;
    private final List<Product> products = new ArrayList<>();
    private final List<CartItem> cartItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        member = member();
        otherMember = member();
        token = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        cartItemRepository.deleteAll(cartItems);
        productRepository.deleteAll(products);
        memberRepository.deleteAll(List.of(member, otherMember));
    }

    @Test
    @DisplayName("본인 장바구니를 담은 순서대로 상품 정보·상태와 함께 돌려주고 다른 회원 항목은 빼고 돌려준다")
    void getCart() throws Exception {
        // given
        Product soybeanPaste = product(MART_ID, "재래식 된장", 4500L, new BigDecimal("500"), SaleUnit.G, 10);
        Product tomato = product(MART_ID, "방울토마토", 3000L, new BigDecimal("500"), SaleUnit.G, 0);
        Product previous = product(OTHER_MART_ID, "두부", 2000L, new BigDecimal("300"), SaleUnit.G, 10);
        CartItem first = cartItem(member, soybeanPaste, 2);
        CartItem second = cartItem(member, tomato, 2);
        CartItem third = cartItem(member, previous, 1);
        cartItem(otherMember, soybeanPaste, 1);

        // when
        MockHttpServletResponse response = getCart(token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
        JsonNode items = data.path("items");
        assertThat(items).hasSize(3);
        assertThat(items.findValuesAsText("cartItemId"))
                .containsExactly(first.getId().toString(), second.getId().toString(), third.getId().toString());
        assertThat(items.findValuesAsText("status")).containsExactly("AVAILABLE", "SOLD_OUT", "PREVIOUS_MART");

        JsonNode firstItem = items.get(0);
        assertThat(firstItem.path("productName").asText()).isEqualTo("재래식 된장");
        assertThat(firstItem.path("price").asLong()).isEqualTo(4500L);
        assertThat(firstItem.path("saleUnitAmount").isTextual()).isTrue();
        // 컬럼이 decimal(10,2)라 저장한 값은 소수 둘째 자리까지 붙어 나온다
        assertThat(firstItem.path("saleUnitAmount").asText()).isEqualTo("500.00");
        assertThat(firstItem.path("saleUnit").asText()).isEqualTo("G");
        assertThat(firstItem.path("quantity").asInt()).isEqualTo(2);
        assertThat(firstItem.path("lineAmount").asLong()).isEqualTo(9000L);
        assertThat(firstItem.path("stock").asInt()).isEqualTo(10);

        assertThat(data.path("hasPreviousMartItems").asBoolean()).isTrue();
        assertThat(data.path("deliveryPolicy").path("freeDeliveryThreshold").asLong()).isEqualTo(40_000L);
        assertThat(data.path("deliveryPolicy").path("deliveryFee").asLong()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("판매 중지·삭제된 상품도 장바구니에 남기고 상태로 표시한다")
    void getCart_stoppedAndDeleted() throws Exception {
        // given
        Product stopped = product(MART_ID, "애호박", 1500L, new BigDecimal("1"), SaleUnit.EA, 10);
        stopped.changeStatus(ProductStatus.STOPPED);
        productRepository.save(stopped);
        Product deleted = product(MART_ID, "대파", 2000L, new BigDecimal("1"), SaleUnit.EA, 10);
        deleted.delete();
        productRepository.save(deleted);
        cartItem(member, stopped, 1);
        cartItem(member, deleted, 1);

        // when
        MockHttpServletResponse response = getCart(token);

        // then
        JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
        assertThat(data.path("items").findValuesAsText("status")).containsExactly("STOPPED", "DELETED");
        assertThat(data.path("hasPreviousMartItems").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("장바구니가 비어 있으면 빈 목록을 돌려준다")
    void getCart_empty() throws Exception {
        // when
        MockHttpServletResponse response = getCart(token);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
        assertThat(data.path("items").isArray()).isTrue();
        assertThat(data.path("items")).isEmpty();
        assertThat(data.path("hasPreviousMartItems").asBoolean()).isFalse();
    }

    private MockHttpServletResponse getCart(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/cart-items").header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse();
    }

    private Member member() {
        return memberRepository.save(Member.builder()
                .email(UUID.randomUUID() + "@cart.test")
                .name("장바구니 회원")
                .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
                .passwordHash("hash")
                .martId(MART_ID)
                .build());
    }

    private Product product(Long martId, String name, Long price, BigDecimal saleUnitAmount, SaleUnit saleUnit,
            int stock) {
        Mart mart = martRepository.findById(martId).orElseThrow();
        Product product = productRepository.save(Product.builder()
                .mart(mart)
                .name(name)
                .price(price)
                .saleUnitAmount(saleUnitAmount)
                .saleUnit(saleUnit)
                .totalQuantity(stock)
                .build());
        products.add(product);
        return product;
    }

    private CartItem cartItem(Member owner, Product product, int quantity) {
        CartItem cartItem = cartItemRepository.save(CartItem.builder()
                .memberId(owner.getId())
                .productId(product.getId())
                .quantity(quantity)
                .build());
        cartItems.add(cartItem);
        return cartItem;
    }
}
