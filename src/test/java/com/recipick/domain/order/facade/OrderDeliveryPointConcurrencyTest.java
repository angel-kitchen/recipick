package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

// 같은 주문에 배송완료가 동시에 와도 주문 행 잠금으로 줄을 서서 첫 요청만 실제로 바꾸고 나머지는 같은 요청 반복으로 끝나는지 본다.
// 회원은 MEMBER_POINT 행 없이 시작해 첫 요청이 행을 만들고 적립을 확정한다
class OrderDeliveryPointConcurrencyTest extends IntegrationTestSupport {

    // data.sql의 시연 마트(삼청 마트)
    private static final Long MART_ID = 1L;
    private static final long EARNING = 50L;
    private static final int THREADS = 5;

    @Autowired
    private OrderDeliveryFacade orderDeliveryFacade;
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
    private Order order;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
            .email(UUID.randomUUID() + "@dc.test")
            .name("동시 배송 회원")
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
        order = orderRepository.save(Order.builder()
            .orderNumber("T" + UUID.randomUUID().toString().substring(0, 20))
            .memberId(member.getId())
            .martId(MART_ID)
            .status(OrderStatus.PAID)
            .martStatus(MartStatus.SHIPPING)
            .productAmount(10_000L)
            .deliveryFee(3_000L)
            .usedPoint(0L)
            .payAmount(13_000L)
            .receiverName("홍길동")
            .receiverPhone("01012345678")
            .address("서울 종로구 삼청로 1")
            .detailAddress("101호")
            .build());
        pointEarningRepository.save(PointEarning.pending(member.getId(), order.getId(), EARNING));
    }

    // 외래키 역순으로 포인트 → 주문 이력 → 주문 → 감사 로그 → 관리자 → 회원 순으로 지운다
    @AfterEach
    void cleanUp() {
        Long memberId = member.getId();
        jdbc.update("DELETE FROM point_history WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM point_earning WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        historyRepository.deleteAll(historyRepository.findAllByOrderId(order.getId()));
        orderRepository.deleteById(order.getId());
        jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", admin.getId());
        adminRepository.deleteById(admin.getId());
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("같은 주문에 배송완료가 5건 동시에 와도 모두 성공하고 주문 전이·포인트 지급·적립 이력·주문 이력·감사는 한 번씩만 남는다")
    void deliveredConcurrently() throws Exception {
        // when
        List<Object> results = runConcurrently(this::deliver);

        // then
        assertThat(results).containsOnly(MartStatus.DELIVERED);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getMartStatus())
            .isEqualTo(MartStatus.DELIVERED);
        PointEarning earning = pointEarningRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(earning.getRemainingAmount()).isEqualTo(EARNING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_point WHERE member_id = ?",
            Integer.class, member.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT balance FROM member_point WHERE member_id = ?",
            Long.class, member.getId())).isEqualTo(EARNING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Integer.class, member.getId(), PointHistoryType.EARN_COMPLETED.name())).isEqualTo(1);
        assertThat(historyRepository.findAllByOrderId(order.getId())).hasSize(1);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ? AND action = 'ORDER_DELIVERY_CHANGE'",
            Integer.class, admin.getId())).isEqualTo(1);
    }

    // 감사 기록이 SecurityContext의 관리자를 읽으므로 작업 스레드마다 넣고 끝나면 비운다
    private Object deliver() {
        AuthPrincipal principal = new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "MART_ADMIN", MART_ID);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
        try {
            return orderDeliveryFacade.changeStatus(
                AdminRole.MART_ADMIN, MART_ID, admin.getId(), order.getId(), MartStatus.DELIVERED).martStatus();
        } catch (BusinessException e) {
            return e.getErrorCode();
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private interface Task {
        Object run();
    }

    private List<Object> runConcurrently(Task task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.run();
            }));
        }
        ready.await();
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }
}
