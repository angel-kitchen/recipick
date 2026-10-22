package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.request.MemberAddressCreateRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.facade.MemberAddressFacade;
import com.recipick.domain.member.repository.MemberAddressRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.security.JwtTokenProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MemberAddressCreateConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MemberRepository members;
    @Autowired
    private MemberAddressRepository addresses;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberAddressFacade facade;

    private Long memberId;
    private String token;

    @BeforeEach
    void setUp() {
        Member member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@race.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("동시 등록 검증")
                .passwordHash("test-password-hash").build());
        memberId = member.getId();
        token = tokens.createMemberTokens(memberId).accessToken();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE member SET selected_address_id = NULL WHERE id = ?", memberId);
        jdbc.update("DELETE FROM member_address WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member WHERE id = ?", memberId);
    }

    @Test
    @DisplayName("4개 보유 회원의 동시 등록 6건은 한 건만 성공하고 총 5개를 유지한다")
    void preventsSixthAddressUnderConcurrency() throws Exception {
        // given
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member member = members.findById(memberId).orElseThrow();
            for (int i = 0; i < 4; i++) {
                MemberAddress address = addresses.save(MemberAddress.builder().member(member)
                        .address("기존 주소").detailAddress("101호").legalDongCode("1111014000").build());
                if (i == 0) {
                    member.selectAddress(address, 1L);
                }
            }
        });

        // when
        List<MockHttpServletResponse> responses = concurrentlyRegister(6);

        // then
        assertThat(responses.stream().filter(response -> response.getStatus() == 201).count()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.getStatus() == 400).count()).isEqualTo(5);
        for (MockHttpServletResponse response : responses) {
            if (response.getStatus() == 400) {
                assertThat(mapper.readTree(response.getContentAsString()).path("code").asText())
                        .isEqualTo("ADDRESS-002");
            }
        }
        assertThat(addresses.countByMemberId(memberId)).isEqualTo(5);
    }

    @Test
    @DisplayName("첫 배송지 두 건이 동시에 등록돼도 자동 선택되는 주소는 하나다")
    void selectsOnlyOneFirstAddress() throws Exception {
        // given
        assertThat(addresses.countByMemberId(memberId)).isZero();

        // when
        List<MockHttpServletResponse> responses = concurrentlyRegister(2);

        // then
        assertThat(responses).allMatch(response -> response.getStatus() == 201);
        List<JsonNode> selected = new ArrayList<>();
        for (MockHttpServletResponse response : responses) {
            JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
            if (data.path("selected").asBoolean()) {
                selected.add(data);
            }
        }
        assertThat(selected).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT selected_address_id FROM member WHERE id = ?", Long.class, memberId))
                .isEqualTo(selected.get(0).path("addressId").asLong());
        assertThat(addresses.countByMemberId(memberId)).isEqualTo(2);
    }

    @Test
    @DisplayName("회원 행 잠금 대기 5초를 초과하면 409 COMMON-010을 반환하고 등록하지 않는다")
    void returnsCommonLockTimeout() throws Exception {
        // given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    members.findByIdForUpdate(memberId).orElseThrow();
                    locked.countDown();
                    try {
                        if (!release.await(15, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("검증용 잠금 해제 대기 시간 초과");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }));
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            long started = System.nanoTime();
            MockHttpServletResponse response = register();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            // then
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(mapper.readTree(response.getContentAsString()).path("code").asText())
                    .isEqualTo("COMMON-010");
            assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(4500));
        } finally {
            release.countDown();
            try {
                holder.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertThat(addresses.countByMemberId(memberId)).isZero();
    }

    @Test
    @DisplayName("등록 후 같은 트랜잭션의 후속 처리가 실패하면 주소·선택·마트·동의를 모두 롤백한다")
    void rollsBackAllChangesWhenTransactionFails() {
        // given
        MemberAddressCreateRequest request = new MemberAddressCreateRequest(
                "집", "테스트 주소", "101호", "1111014000", true, true);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            facade.createAddress(memberId, request);
            throw new IllegalStateException("검증용 후속 처리 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("검증용 후속 처리 실패");

        // then
        assertThat(addresses.countByMemberId(memberId)).isZero();
        assertThat(jdbc.queryForObject("SELECT selected_address_id FROM member WHERE id = ?", Long.class, memberId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT mart_id FROM member WHERE id = ?", Long.class, memberId)).isNull();
        assertThat(jdbc.queryForObject("SELECT location_agreed_at FROM member WHERE id = ?",
                java.sql.Timestamp.class, memberId)).isNull();
    }

    private List<MockHttpServletResponse> concurrentlyRegister(int count) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
                    }
                    return register();
                }));
            }
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MockHttpServletResponse register() throws Exception {
        return mockMvc.perform(post("/api/members/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("address", "테스트 주소", "detailAddress", "101호",
                        "legalDongCode", "1111014000")))).andReturn().getResponse();
    }
}
