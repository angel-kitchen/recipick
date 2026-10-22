package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.repository.MemberAddressRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberAddressSelectionOrderIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private MemberAddressRepository addresses;
    @Autowired
    private MemberService memberService;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private ProductRepository products;
    @Autowired
    private MartRepository marts;
    @Autowired
    private CartItemRepository cartItems;
    @Autowired
    private OrderRepository orders;

    private Member member;
    private MemberAddress firstAddress;
    private MemberAddress otherAddress;
    private String token;

    @BeforeEach
    void setUp() {
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@so.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("배송지 주문 연동 검증")
                .passwordHash("hash").build());
        firstAddress = address("삼청 저장 주소", "1111014000");
        otherAddress = address("하안 저장 주소", "4121010300");
        member.selectAddress(firstAddress, 1L);
        members.flush();
        token = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @Test
    @DisplayName("배송지 선택 후 주문은 변경된 마트를 사용하고 일회성 배송지는 회원 주소에 반영하지 않는다")
    void usesSelectedMartAndKeepsOneTimeOrderAddressSeparate() throws Exception {
        // given
        CartItem item = cartItem(2L);
        MockHttpServletResponse selection = select(otherAddress.getId());
        assertThat(selection.getStatus()).isEqualTo(200);

        // when
        MockHttpServletResponse response = order(item.getId(), "4121010300");

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        Long orderId = mapper.readTree(response.getContentAsString()).path("data").path("orderId").asLong();
        Order order = orders.findById(orderId).orElseThrow();
        assertThat(order.getMartId()).isEqualTo(2L);
        assertThat(order.getRegionId()).isEqualTo(2L);
        assertThat(order.getAddress()).isEqualTo("이번 주문만 사용할 주소");
        assertThat(order.getDetailAddress()).isEqualTo("이번 주문 상세 주소");
        assertThat(memberService.getMartId(member.getId())).contains(2L);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(otherAddress.getId());
        assertThat(otherAddress.getAddress()).isEqualTo("하안 저장 주소");
        assertThat(otherAddress.getDetailAddress()).isEqualTo("저장 상세 주소");
        assertThat(firstAddress.getAddress()).isEqualTo("삼청 저장 주소");
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(2);
        assertThat(cartItems.findById(item.getId())).isPresent();
    }

    @Test
    @DisplayName("배송지 선택으로 마트가 바뀌면 이전 마트 상품 주문을 거절하고 재고를 유지한다")
    void rejectsPreviousMartProductsAfterSelection() throws Exception {
        // given
        CartItem previousMartItem = cartItem(1L);
        assertThat(select(otherAddress.getId()).getStatus()).isEqualTo(200);

        // when
        MockHttpServletResponse response = order(previousMartItem.getId(), "4121010300");

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("CART-006");
        assertThat(orders.findIdsByMemberId(member.getId())).isEmpty();
        assertThat(products.findById(previousMartItem.getProductId()).orElseThrow().getTotalQuantity()).isEqualTo(10);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(otherAddress.getId());
        assertThat(memberService.getMartId(member.getId())).contains(2L);
    }

    @Test
    @DisplayName("일회성 배송지가 다른 마트 지역이면 주문만 거절하고 현재 배송지와 이용 마트를 유지한다")
    void rejectsOtherRegionWithoutChangingMemberSelection() throws Exception {
        // given
        CartItem item = cartItem(1L);

        // when
        MockHttpServletResponse response = order(item.getId(), "4121010300");

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("ORDER-008");
        assertThat(orders.findIdsByMemberId(member.getId())).isEmpty();
        assertThat(member.getSelectedAddress().getId()).isEqualTo(firstAddress.getId());
        assertThat(memberService.getMartId(member.getId())).contains(1L);
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(2);
        assertThat(products.findById(item.getProductId()).orElseThrow().getTotalQuantity()).isEqualTo(10);
    }

    private MemberAddress address(String address, String code) {
        return addresses.saveAndFlush(MemberAddress.builder().member(member).address(address)
                .detailAddress("저장 상세 주소").legalDongCode(code).build());
    }

    private CartItem cartItem(Long martId) {
        Product product = products.saveAndFlush(Product.builder().mart(marts.findById(martId).orElseThrow())
                .name("주문 연동 검증 상품").price(1000L).saleUnit(SaleUnit.EA)
                .saleUnitAmount(BigDecimal.ONE).totalQuantity(10).build());
        return cartItems.saveAndFlush(CartItem.builder().memberId(member.getId())
                .productId(product.getId()).quantity(1).build());
    }

    private MockHttpServletResponse select(Long addressId) throws Exception {
        return mockMvc.perform(put("/api/members/me/addresses/{id}/select", addressId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
    }

    private MockHttpServletResponse order(Long cartItemId, String legalDongCode) throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId), "이번 주문 수령인", "01012345678",
                "이번 주문만 사용할 주소", "이번 주문 상세 주소", legalDongCode);
        return mockMvc.perform(post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andReturn().getResponse();
    }

    private String code(MockHttpServletResponse response) throws Exception {
        JsonNode body = mapper.readTree(response.getContentAsString());
        return body.path("code").asText();
    }
}
