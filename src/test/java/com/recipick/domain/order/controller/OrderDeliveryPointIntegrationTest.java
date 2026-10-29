package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 배송 상태 변경과 배송완료 적립 확정을 실제 DB·보안·감사까지 이어서 확인한다.
// 회원은 포인트를 써 본 적이 없어 MEMBER_POINT 행이 없고, 이 주문의 적립 예정 건만 있는 상태에서 시작한다 —
// 주문 생성이 만드는 상태 그대로다. 잔액만 넣어 두면 잔액 = 확정 적립 건 잔여 합이라는 원장 규칙이 깨진다
class OrderDeliveryPointIntegrationTest extends IntegrationTestSupport {

    // data.sql의 시연 마트(삼청 마트)
    private static final Long MART_ID = 1L;
    // 상품금액 10,000원의 0.5% 적립 예정
    private static final long EARNING = 50L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderStatusHistoryRepository historyRepository;
    @Autowired
    private PointEarningRepository pointEarningRepository;

    private Member member;
    private Admin admin;
    private String adminToken;
    private Order order;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
            .email(UUID.randomUUID() + "@dl.test")
            .name("배송 회원")
            .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
            .passwordHash("hash")
            .martId(MART_ID)
            .build());
        admin = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .passwordHash("hash")
            .role(AdminRole.MART_ADMIN)
            .martId(MART_ID)
            .active(true)
            .build());
        adminToken = tokenProvider.createTokens(
            new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "MART_ADMIN", MART_ID)).accessToken();
    }

    // 외래키 역순으로 포인트 → 주문 이력 → 주문 → 감사 로그 → 관리자 → 회원 순으로 지운다
    @AfterEach
    void cleanUp() {
        Long memberId = member.getId();
        jdbc.update("DELETE FROM point_history WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM point_earning WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        if (order != null) {
            historyRepository.deleteAll(historyRepository.findAllByOrderId(order.getId()));
            orderRepository.deleteById(order.getId());
        }
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", admin.getId());
        adminRepository.deleteById(admin.getId());
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("배송중으로 바꾸면 주문 이력·감사만 남고 적립 예정 건과 회원 포인트는 그대로다")
    void shipping() throws Exception {
        // given
        order = paidOrder(MartStatus.PREPARING);
        pointEarningRepository.save(PointEarning.pending(member.getId(), order.getId(), EARNING));

        // when
        MockHttpServletResponse response = changeStatus("SHIPPING");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("status").asText()).isEqualTo("PAID");
        assertThat(data(response).path("martStatus").asText()).isEqualTo("SHIPPING");
        Order saved = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getMartStatus()).isEqualTo(MartStatus.SHIPPING);
        PointEarning earning = earning();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getRemainingAmount()).isZero();
        assertThat(memberPointCount()).isZero();
        assertThat(historyCount(PointHistoryType.EARN_COMPLETED)).isZero();
        List<OrderStatusHistory> histories = historyRepository.findAllByOrderId(order.getId());
        assertThat(histories).hasSize(1);
        OrderStatusHistory history = histories.get(0);
        assertThat(history.getStatusType()).isEqualTo(OrderStatusType.MART);
        assertThat(history.getFromStatus()).isEqualTo("PREPARING");
        assertThat(history.getToStatus()).isEqualTo("SHIPPING");
        assertThat(history.getActor()).isEqualTo(OrderStatusActor.MART);
        assertThat(history.getChangedByAdminId()).isEqualTo(admin.getId());
        assertThat(deliveryAuditCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("배송중을 거쳐 배송완료가 되면 적립 예정 건이 확정되고 처음 만들어진 회원 포인트에 적립액만큼 쌓인다")
    void delivered() throws Exception {
        // given
        order = paidOrder(MartStatus.PREPARING);
        pointEarningRepository.save(PointEarning.pending(member.getId(), order.getId(), EARNING));
        changeStatus("SHIPPING");

        // when
        MockHttpServletResponse response = changeStatus("DELIVERED");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("martStatus").asText()).isEqualTo("DELIVERED");
        Order saved = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getMartStatus()).isEqualTo(MartStatus.DELIVERED);
        PointEarning earning = earning();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(earning.getAmount()).isEqualTo(EARNING);
        assertThat(earning.getRemainingAmount()).isEqualTo(EARNING);
        assertThat(earning.getConfirmedAt()).isNotNull();
        assertThat(earning.getExpiresOn()).isEqualTo(earning.getConfirmedAt().toLocalDate().plusYears(1));
        assertThat(memberPointCount()).isEqualTo(1);
        assertThat(balance()).isEqualTo(EARNING);
        assertThat(historyCount(PointHistoryType.EARN_COMPLETED)).isEqualTo(1);
        assertThat(earnCompletedAmount()).isEqualTo(EARNING);
        assertThat(historyRepository.findAllByOrderId(order.getId()))
            .extracting(OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus)
            .containsExactlyInAnyOrder(tuple("PREPARING", "SHIPPING"), tuple("SHIPPING", "DELIVERED"));
        assertThat(deliveryAuditCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("배송완료를 여러 번 보내도 주문 전이·포인트 지급·이력·감사는 한 번씩만 반영된다")
    void deliveredRepeatedly() throws Exception {
        // given
        order = paidOrder(MartStatus.SHIPPING);
        pointEarningRepository.save(PointEarning.pending(member.getId(), order.getId(), EARNING));

        // when
        List<Integer> statuses = List.of(
            changeStatus("DELIVERED").getStatus(),
            changeStatus("DELIVERED").getStatus(),
            changeStatus("DELIVERED").getStatus());

        // then
        assertThat(statuses).containsOnly(200);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getMartStatus())
            .isEqualTo(MartStatus.DELIVERED);
        assertThat(earning().getRemainingAmount()).isEqualTo(EARNING);
        assertThat(memberPointCount()).isEqualTo(1);
        assertThat(balance()).isEqualTo(EARNING);
        assertThat(historyCount(PointHistoryType.EARN_COMPLETED)).isEqualTo(1);
        assertThat(historyRepository.findAllByOrderId(order.getId())).hasSize(1);
        assertThat(deliveryAuditCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("적립 예정이 0P여도 배송완료면 적립 건은 확정되고 회원 포인트 행은 0P로 생기며 적립 이력은 남지 않는다")
    void deliveredWithZeroEarning() throws Exception {
        // given: 포인트로 전액 결제한 주문처럼 적립 예정이 0P다
        order = paidOrder(MartStatus.SHIPPING);
        pointEarningRepository.save(PointEarning.pending(member.getId(), order.getId(), 0L));

        // when
        MockHttpServletResponse response = changeStatus("DELIVERED");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        PointEarning earning = earning();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(earning.getRemainingAmount()).isZero();
        assertThat(earning.getConfirmedAt()).isNotNull();
        assertThat(earning.getExpiresOn()).isNotNull();
        // 적립 확정이 적립 건보다 회원 포인트를 먼저 잠그므로, 0P여도 회원 포인트 행은 잔액 0으로 만들어진다
        assertThat(memberPointCount()).isEqualTo(1);
        assertThat(balance()).isZero();
        assertThat(historyCount(PointHistoryType.EARN_COMPLETED)).isZero();
    }

    @Test
    @DisplayName("포인트 도입 전 주문처럼 적립 예정 건이 없으면 배송완료만 반영되고 회원 포인트 행·포인트 이력은 생기지 않는다")
    void deliveredWithoutEarning() throws Exception {
        // given
        order = paidOrder(MartStatus.SHIPPING);

        // when
        MockHttpServletResponse response = changeStatus("DELIVERED");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getMartStatus())
            .isEqualTo(MartStatus.DELIVERED);
        assertThat(memberPointCount()).isZero();
        assertThat(pointHistoryCount()).isZero();
    }

    @Test
    @DisplayName("적립 확정이 실패하면 이미 반영한 배송완료·주문 이력·감사와 중간에 만든 회원 포인트 행까지 모두 롤백된다")
    void rollbackWhenEarningConfirmFails() throws Exception {
        // given: 정상 흐름에서는 생길 수 없는 손상 데이터로 적립 확정을 일부러 실패시킨다(취소된 적립 건은 확정할 수 없다)
        order = paidOrder(MartStatus.SHIPPING);
        PointEarning canceled = PointEarning.pending(member.getId(), order.getId(), EARNING);
        canceled.cancel();
        pointEarningRepository.save(canceled);

        // when
        MockHttpServletResponse response = changeStatus("DELIVERED");

        // then: 500은 업무 오류 계약이 아니라 롤백을 확인하기 위한 실패 신호다
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(code(response)).isEqualTo("COMMON-500");
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getMartStatus())
            .isEqualTo(MartStatus.SHIPPING);
        assertThat(historyRepository.findAllByOrderId(order.getId())).isEmpty();
        assertThat(deliveryAuditCount()).isZero();
        assertThat(earning().getStatus()).isEqualTo(PointEarningStatus.CANCELED);
        assertThat(memberPointCount()).isZero();
        assertThat(pointHistoryCount()).isZero();
    }

    private Order paidOrder(MartStatus martStatus) {
        return orderRepository.save(Order.builder()
            .orderNumber("T" + UUID.randomUUID().toString().substring(0, 20))
            .memberId(member.getId())
            .martId(MART_ID)
            .status(OrderStatus.PAID)
            .martStatus(martStatus)
            .productAmount(10_000L)
            .deliveryFee(3_000L)
            .usedPoint(0L)
            .payAmount(13_000L)
            .receiverName("홍길동")
            .receiverPhone("01012345678")
            .address("서울 종로구 삼청로 1")
            .detailAddress("101호")
            .build());
    }

    private MockHttpServletResponse changeStatus(String status) throws Exception {
        return mockMvc.perform(patch("/api/admin/orders/{orderId}/delivery-status", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}"))
            .andReturn().getResponse();
    }

    private PointEarning earning() {
        return pointEarningRepository.findByOrderId(order.getId()).orElseThrow();
    }

    private int memberPointCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member_point WHERE member_id = ?",
            Integer.class, member.getId());
    }

    // 잠금 조회는 트랜잭션이 필요해 값만 직접 읽는다
    private long balance() {
        return jdbc.queryForObject("SELECT balance FROM member_point WHERE member_id = ?",
            Long.class, member.getId());
    }

    private int historyCount(PointHistoryType type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Integer.class, member.getId(), type.name());
    }

    private long earnCompletedAmount() {
        return jdbc.queryForObject("SELECT amount FROM point_history WHERE member_id = ? AND type = ?",
            Long.class, member.getId(), PointHistoryType.EARN_COMPLETED.name());
    }

    private int pointHistoryCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ?",
            Integer.class, member.getId());
    }

    private int deliveryAuditCount() {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ? AND action = 'ORDER_DELIVERY_CHANGE'",
            Integer.class, admin.getId());
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    private String code(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("code").asText();
    }
}
