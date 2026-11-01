package com.recipick.domain.payment.integration;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.payment.config.MembershipBillingGate;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

// 실제 실행기(PG 스텁 포함)를 끝까지 타는 흐름 테스트의 공통 바닥. PortOne 주소를 스텁으로 바꾸고(컨텍스트 분리),
// 플래그는 토글 Gate로, 시계는 고정·이동할 수 있는 MovableClock으로 바꿔 끼운다(@Primary — 운영 빈은 그대로 두고 주입만 바꾼다).
// MovableClock은 기본이 시스템 시각(Asia/Seoul)이고 테스트가 고정하면 Executor·Processor·Facade가 모두 같은 시각을 본다.
// 읽기 한도는 POST를 잠깐 붙잡는 동시성 테스트를 위해 늘린다
@Import(MembershipChargeFlowSupport.ToggleGateConfig.class)
public abstract class MembershipChargeFlowSupport extends IntegrationTestSupport {

    protected static final PortOneStubServer PORTONE = new PortOneStubServer();
    protected static final String PASSWORD = "Original1!";
    protected static final long FEE = 3_900L;

    @DynamicPropertySource
    static void portOneStub(DynamicPropertyRegistry registry) {
        registry.add("portone.base-url", PORTONE::baseUrl);
        registry.add("portone.read-timeout", () -> "15s");
    }

    @TestConfiguration
    static class ToggleGateConfig {

        @Bean
        @Primary
        ToggleGate toggleGate() {
            return new ToggleGate();
        }

        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    public static class MovableClock extends Clock {

        private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
        private final AtomicReference<Instant> fixed = new AtomicReference<>();

        public void fixAt(LocalDateTime kst) {
            fixed.set(kst.atZone(ZONE).toInstant());
        }

        public void reset() {
            fixed.set(null);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            Instant at = fixed.get();
            return at == null ? Instant.now() : at;
        }
    }

    public static class ToggleGate implements MembershipBillingGate {

        private final AtomicBoolean enabled = new AtomicBoolean(true);

        public void set(boolean on) {
            enabled.set(on);
        }

        @Override
        public boolean newChargeEnabled() {
            return enabled.get();
        }
    }

    @Autowired protected ToggleGate gate;
    @Autowired protected MemberRepository members;
    @Autowired protected BillingKeyRepository billingKeys;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MovableClock clock;
    @Autowired protected EntityManager em;
    @Autowired protected TransactionTemplate transactionTemplate;

    protected final List<Long> memberIds = new ArrayList<>();
    protected MembershipTimeFixtures time;
    // 동시성 테스트의 작업 스레드가 끝났는지. 끝나지 않았으면 정리를 건너뛴다(아직 도는 트랜잭션과 겹치지 않게)
    protected boolean workersTerminated = true;

    @BeforeEach
    void resetStubAndGate() {
        PORTONE.reset();
        gate.set(true);
        clock.reset();
        time = new MembershipTimeFixtures(em, transactionTemplate);
    }

    @AfterEach
    void cleanUpMembers() {
        gate.set(true);
        clock.reset();
        PORTONE.releasePosts();
        if (!workersTerminated) {
            return;
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE r FROM membership_refund r JOIN membership_payment p ON r.membership_payment_id = p.id "
                + "JOIN membership m ON p.membership_id = m.id WHERE m.member_id = ?", memberId);
            jdbc.update("DELETE a FROM membership_payment_attempt a JOIN membership_payment p "
                    + "ON a.membership_payment_id = p.id JOIN membership m ON p.membership_id = m.id WHERE m.member_id = ?",
                memberId);
            jdbc.update("DELETE p FROM membership_payment p JOIN membership m ON p.membership_id = m.id "
                + "WHERE m.member_id = ?", memberId);
            jdbc.update("DELETE FROM membership WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM billing_key WHERE member_id = ?", memberId);
            members.deleteById(memberId);
        }
        memberIds.clear();
    }

    protected Member saveMember() {
        String suffix = UUID.randomUUID().toString();
        Member saved = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
            .name("흐름테스트").passwordHash(encoder.encode(PASSWORD)).build());
        memberIds.add(saved.getId());
        return saved;
    }

    protected BillingKey saveActiveKey(Long memberId) {
        return billingKeys.saveAndFlush(BillingKey.builder().memberId(memberId)
            .portoneBillingKey("billing-key-test-" + UUID.randomUUID()).status(BillingKeyStatus.ACTIVE)
            .issuedAt(LocalDateTime.now(clock)).build());
    }
}
