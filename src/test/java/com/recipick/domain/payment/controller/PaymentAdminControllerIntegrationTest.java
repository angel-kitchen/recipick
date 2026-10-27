package com.recipick.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.AdminAuditLog;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminAuditLogRepository;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 관리자 결제 조회를 실제 MySQL·보안 필터·감사 기록까지 태워 검증한다. QueryDSL 조건도 실제 쿼리로 확인한다.
// MockMvc는 테스트와 같은 스레드에서 돌아 Facade 트랜잭션이 테스트 트랜잭션에 합류한다 — 데이터와 감사 기록은 끝나면 롤백된다.
// 다른 테스트가 남긴 결제와 섞이지 않도록, 기간 조회 대상 결제의 created_at을 2020-01로 옮겨 그 구간만 조회한다
@Transactional
class PaymentAdminControllerIntegrationTest extends IntegrationTestSupport {

    private static final String URL = "/api/admin/payments";
    private static final long AMOUNT = 23800L;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2020, 1, 1, 0, 30, 0);
    // 주문의 member_id는 회원 테이블과 외래키가 없어 실제 회원 없이 번호만 겹치지 않게 쓴다
    private static final AtomicLong MEMBER_SEQUENCE = new AtomicLong(96_000L);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private AdminAuditLogRepository auditLogRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private EntityManager entityManager;

    private Long superAdminId;
    private String superToken;
    private long memberId;
    private long otherMemberId;

    // memberId 회원: 2020-01-01 00:00:00 취소 완료(환불 거절 후 재시도 완료) / 01-02 23:59:59 실패 / 01-03 00:00:00 진행 중
    // otherMemberId 회원: 01-02 12:00:00 결제 완료
    private Long canceledId;
    private Long canceledOrderId;
    private Long failedId;
    private Long inProgressId;
    private Long otherPaidId;
    private Long rejectedRefundId;
    private Long completedRefundId;

    @BeforeEach
    void setUp() {
        // 감사 기록은 토큰의 관리자 ID로 ADMIN 행을 다시 확인하므로 활성 SUPER_ADMIN을 실제로 저장한다
        Admin superAdmin = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .name("결제 조회 슈퍼 관리자")
            .passwordHash("hash")
            .role(AdminRole.SUPER_ADMIN)
            .active(true)
            .build());
        superAdminId = superAdmin.getId();
        superToken = token(superAdminId, "SUPER_ADMIN", null);
        memberId = MEMBER_SEQUENCE.incrementAndGet();
        otherMemberId = MEMBER_SEQUENCE.incrementAndGet();

        Payment canceled = paidPayment(memberId);
        canceledId = canceled.getId();
        canceledOrderId = canceled.getOrderId();
        // 첫 시도는 PG가 처리 전에 거절하고, 다시 요청한 두 번째 시도가 완료된 결제다
        canceled.requestCancel();
        Refund rejected = refundRepository.save(
            Refund.requestedByMember(canceled, "단순 변심", UUID.randomUUID().toString(), PAID_AT.plusMinutes(10)));
        rejected.fail(null, RefundFailureType.REQUEST_REJECTED, "PG 요청 거절");
        canceled.restorePaid();
        canceled.requestCancel();
        Refund completed = refundRepository.save(
            Refund.requestedByMember(canceled, "단순 변심", UUID.randomUUID().toString(), PAID_AT.plusMinutes(20)));
        completed.complete("cancel_admin_" + canceledId, COMPLETED_AT);
        canceled.markCanceled(COMPLETED_AT);
        rejectedRefundId = rejected.getId();
        completedRefundId = completed.getId();

        failedId = failedPayment(memberId).getId();
        inProgressId = paymentRepository.save(newPayment(memberId)).getId();
        otherPaidId = paidPayment(otherMemberId).getId();

        entityManager.flush();
        moveCreatedAt(canceledId, LocalDateTime.of(2020, 1, 1, 0, 0, 0));
        moveCreatedAt(failedId, LocalDateTime.of(2020, 1, 2, 23, 59, 59));
        moveCreatedAt(inProgressId, LocalDateTime.of(2020, 1, 3, 0, 0, 0));
        moveCreatedAt(otherPaidId, LocalDateTime.of(2020, 1, 2, 12, 0, 0));
        // 1차 캐시에 남은 옛 created_at 대신 DB 값을 읽도록 비운다
        entityManager.clear();
    }

    @Test
    @DisplayName("회원 조건 목록은 그 회원 결제만 최신순으로 주고, 회원 ID·환불 요약을 붙이며 PAYMENT_LIST_VIEW를 한 번 기록한다")
    void findPaymentsOfMember() throws Exception {
        // given
        String url = URL + "?memberId=" + memberId;

        // when
        MockHttpServletResponse response = request(superToken, url);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = body(response).path("data");
        assertThat(paymentIds(data)).containsExactly(inProgressId, failedId, canceledId);
        assertThat(data.path("totalElements").asLong()).isEqualTo(3);
        assertThat(data.path("page").asInt()).isZero();
        assertThat(data.path("size").asInt()).isEqualTo(20);

        JsonNode inProgress = data.path("content").get(0);
        assertThat(inProgress.path("memberId").asLong()).isEqualTo(memberId);
        assertThat(inProgress.path("refundedAmount").asLong()).isZero();
        assertThat(inProgress.path("latestRefundStatus").isNull()).isTrue();
        assertThat(inProgress.has("portonePaymentId")).isFalse();

        JsonNode canceled = data.path("content").get(2);
        assertThat(canceled.path("paymentStatus").asText()).isEqualTo("CANCELED");
        assertThat(canceled.path("refundedAmount").asLong()).isEqualTo(AMOUNT);
        assertThat(canceled.path("latestRefundStatus").asText()).isEqualTo("COMPLETED");

        List<AdminAuditLog> audits = auditsOfSuperAdmin();
        assertThat(audits).singleElement().satisfies(audit -> {
            assertThat(audit.getAction()).isEqualTo(AdminAuditAction.PAYMENT_LIST_VIEW);
            assertThat(audit.getTargetId()).isNull();
        });
    }

    @Test
    @DisplayName("기간은 from 00:00부터 to 다음 날 00:00 미만까지이고, createdAt 오름차순 정렬을 따른다")
    void findPaymentsInPeriod() throws Exception {
        // given
        String url = URL + "?from=2020-01-01&to=2020-01-02&sort=createdAt,asc";

        // when
        MockHttpServletResponse response = request(superToken, url);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        // 01-03 00:00:00 결제는 to 다음 날 00:00과 같아서 빠진다
        assertThat(paymentIds(body(response).path("data"))).containsExactly(canceledId, otherPaidId, failedId);
    }

    @Test
    @DisplayName("상태 조건과 기간 조건을 함께 걸면 둘 다 맞는 결제만 준다")
    void findPaymentsByStatusInPeriod() throws Exception {
        // given
        String url = URL + "?status=FAILED&from=2020-01-01&to=2020-01-03";

        // when
        MockHttpServletResponse response = request(superToken, url);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(paymentIds(body(response).path("data"))).containsExactly(failedId);
    }

    @Test
    @DisplayName("page·size로 자르고 전체 건수·페이지 수를 함께 준다")
    void findPaymentsPage() throws Exception {
        // given
        String url = URL + "?memberId=" + memberId + "&page=1&size=2";

        // when
        MockHttpServletResponse response = request(superToken, url);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = body(response).path("data");
        assertThat(paymentIds(data)).containsExactly(canceledId);
        assertThat(data.path("totalElements").asLong()).isEqualTo(3);
        assertThat(data.path("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문이 없는 회원은 빈 페이지지만, 조회 자체는 PAYMENT_LIST_VIEW로 기록한다")
    void findPaymentsOfMemberWithoutOrders() throws Exception {
        // given
        long noOrderMemberId = MEMBER_SEQUENCE.incrementAndGet();

        // when
        MockHttpServletResponse response = request(superToken, URL + "?memberId=" + noOrderMemberId);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = body(response).path("data");
        assertThat(data.path("content").isEmpty()).isTrue();
        assertThat(data.path("totalElements").asLong()).isZero();
        assertThat(auditsOfSuperAdmin()).extracting(AdminAuditLog::getAction)
            .containsExactly(AdminAuditAction.PAYMENT_LIST_VIEW);
    }

    @Test
    @DisplayName("상세는 회원 ID·PortOne ID와 실패한 시도를 포함한 환불 이력 전체를 최신순으로 주고 PAYMENT_DETAIL_VIEW를 기록한다")
    void getPayment() throws Exception {
        // given
        String url = URL + "/" + canceledId;

        // when
        MockHttpServletResponse response = request(superToken, url);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = body(response).path("data");
        assertThat(data.path("paymentId").asLong()).isEqualTo(canceledId);
        assertThat(data.path("memberId").asLong()).isEqualTo(memberId);
        assertThat(data.path("paymentStatus").asText()).isEqualTo("CANCELED");
        assertThat(data.path("portonePaymentId").asText()).isEqualTo("pay_admin_" + canceledOrderId);
        assertThat(data.path("portoneTransactionId").asText()).isEqualTo("tx_admin_" + canceledOrderId);

        JsonNode refunds = data.path("refunds");
        assertThat(refunds.size()).isEqualTo(2);
        assertThat(refunds.get(0).path("refundId").asLong()).isEqualTo(completedRefundId);
        assertThat(refunds.get(0).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(refunds.get(0).path("portoneCancellationId").asText()).isEqualTo("cancel_admin_" + canceledId);
        assertThat(refunds.get(1).path("refundId").asLong()).isEqualTo(rejectedRefundId);
        assertThat(refunds.get(1).path("status").asText()).isEqualTo("FAILED");
        assertThat(refunds.get(1).path("failureReason").asText()).isEqualTo("PG 요청 거절");
        assertThat(refunds.get(1).has("idempotencyKey")).isFalse();

        assertThat(auditsOfSuperAdmin()).singleElement().satisfies(audit -> {
            assertThat(audit.getAction()).isEqualTo(AdminAuditAction.PAYMENT_DETAIL_VIEW);
            assertThat(audit.getTargetId()).isEqualTo(canceledId);
        });
    }

    @Test
    @DisplayName("없는 결제 상세는 404 PAYMENT-001이고 감사 기록을 남기지 않는다")
    void getPaymentNotFound() throws Exception {
        // given
        String url = URL + "/" + Long.MAX_VALUE;

        // when
        MockHttpServletResponse response = request(superToken, url);

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(body(response).path("code").asText()).isEqualTo("PAYMENT-001");
        assertThat(auditsOfSuperAdmin()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "status=DONE",
        "memberId=0",
        "memberId=abc",
        "from=2020/01/01",
        "from=2020-02-30",
        "from=2020-01-02&to=2020-01-01",
        "page=-1",
        "page=abc",
        "size=0",
        "size=101",
        "size=abc",
        "sort=amount,desc",
        "sort=createdAt,up"
    })
    @DisplayName("잘못된 필터·기간·페이징은 400 COMMON-001이고 감사 기록을 남기지 않는다")
    void rejectsInvalidQuery(String query) throws Exception {
        // given
        String url = URL + "?" + query;

        // when
        MockHttpServletResponse response = request(superToken, url);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body(response).path("code").asText()).isEqualTo("COMMON-001");
        assertThat(auditsOfSuperAdmin()).isEmpty();
    }

    @Test
    @DisplayName("MART_ADMIN은 목록·상세 모두 403 AUTH-006이다")
    void rejectsMartAdmin() throws Exception {
        // given
        String martToken = token(Long.MAX_VALUE, "MART_ADMIN", 1L);

        // when
        MockHttpServletResponse list = request(martToken, URL);
        MockHttpServletResponse detail = request(martToken, URL + "/" + canceledId);

        // then
        assertThat(list.getStatus()).isEqualTo(403);
        assertThat(body(list).path("code").asText()).isEqualTo("AUTH-006");
        assertThat(detail.getStatus()).isEqualTo(403);
        assertThat(body(detail).path("code").asText()).isEqualTo("AUTH-006");
    }

    @Test
    @DisplayName("회원 토큰은 관리자 경로에 들어올 수 없어 403이다")
    void rejectsMember() throws Exception {
        // given
        // 회원 토큰은 인증 필터가 회원 상태를 DB에서 확인하므로 실제 회원을 저장한다
        String unique = UUID.randomUUID()
            .toString()
            .replace("-", "");

        Member member = memberRepository.saveAndFlush(
            Member.builder()
                .email(unique + "@pay.test")
                .phone(unique.substring(0, 20))
                .name("결제 조회 회원")
                .passwordHash("hash")
                .build()
        );

        String memberToken =
            tokens.createMemberTokens(member.getId()).accessToken();

        // when
        MockHttpServletResponse response = request(memberToken, URL);

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(auditsOfSuperAdmin()).isEmpty();
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void rejectsAnonymous() throws Exception {
        // when
        MockHttpServletResponse response = request(null, URL);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ---------- 픽스처 ----------

    private Payment newPayment(long ownerId) {
        Order order = orderRepository.save(Order.builder()
            .memberId(ownerId)
            .martId(1L)
            .status(OrderStatus.PAID)
            .martStatus(MartStatus.RECEIVED)
            .build());
        return Payment.builder()
            .orderId(order.getId())
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_admin_" + order.getId())
            .amount(AMOUNT)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(PAID_AT.plusMinutes(5))
            .build();
    }

    // 결제 완료 상태는 빌더로 못 만들고 실제 전이를 거쳐야 한다
    private Payment paidPayment(long ownerId) {
        Payment payment = newPayment(ownerId);
        payment.markPaid("tx_admin_" + payment.getOrderId(), PAID_AT);
        return paymentRepository.save(payment);
    }

    private Payment failedPayment(long ownerId) {
        Payment payment = newPayment(ownerId);
        payment.fail(PaymentFailureReason.PG_FAILED, null);
        return paymentRepository.save(payment);
    }

    // created_at은 생성 시각 자동 기록이라 엔티티로 못 바꾼다. 조회 조건과 같은 Hibernate 바인딩을 거치도록
    // JdbcTemplate 대신 EntityManager 네이티브 쿼리로 옮긴다 — 테스트 JVM 시간대가 달라도 쓰기·조회 기준이 같다
    private void moveCreatedAt(Long paymentId, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE payment SET created_at = :createdAt WHERE id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", paymentId)
            .executeUpdate();
    }

    // ---------- 요청·응답 ----------

    private String token(Long adminId, String role, Long martId) {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, adminId, role, martId)).accessToken();
    }

    private MockHttpServletResponse request(String token, String url) throws Exception {
        MockHttpServletRequestBuilder builder = get(url);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private List<Long> paymentIds(JsonNode data) {
        List<Long> ids = new ArrayList<>();
        data.path("content").forEach(row -> ids.add(row.path("paymentId").asLong()));
        return ids;
    }

    private List<AdminAuditLog> auditsOfSuperAdmin() {
        return auditLogRepository.findAll().stream()
            .filter(audit -> audit.getAdmin().getId().equals(superAdminId))
            .toList();
    }
}
