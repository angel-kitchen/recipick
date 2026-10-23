package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.facade.MemberAddressFacade;
import com.recipick.domain.member.repository.MemberAddressRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.security.JwtTokenProvider;
import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MemberAddressDeleteConcurrencyTest extends IntegrationTestSupport {

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
    private Long addressId;
    private Long otherAddressId;

    @BeforeEach
    void setUp() {
        Member member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@race.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("동시 삭제 검증")
                .passwordHash("test-password-hash").build());
        memberId = member.getId();
        token = tokens.createMemberTokens(memberId).accessToken();
        addressId = new TransactionTemplate(transactionManager).execute(status -> {
            Member owner = members.findById(memberId).orElseThrow();
            MemberAddress address = addresses.saveAndFlush(MemberAddress.builder().member(owner).label("집")
                    .address("기존 주소").detailAddress("101호").legalDongCode("1111014000").build());
            owner.selectAddress(address, 1L);
            otherAddressId = addresses.saveAndFlush(MemberAddress.builder().member(owner).label("회사")
                    .address("하안 주소").detailAddress("202호").legalDongCode("4121010300").build()).getId();
            return address.getId();
        });
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE member SET selected_address_id = NULL WHERE id = ?", memberId);
        jdbc.update("DELETE FROM member_address WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member WHERE id = ?", memberId);
    }

    @Test
    @DisplayName("회원 행 잠금 대기 5초를 초과하면 409 COMMON-010을 반환하고 삭제하지 않는다")
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
            MockHttpServletResponse response = deleteAddress(otherAddressId);
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
        assertThat(addresses.existsById(otherAddressId)).isTrue();
        assertThat(addresses.countByMemberId(memberId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT selected_address_id FROM member WHERE id = ?", Long.class, memberId))
                .isEqualTo(addressId);
        assertThat(jdbc.queryForObject("SELECT mart_id FROM member WHERE id = ?", Long.class, memberId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("삭제 후 후속 처리가 실패하면 주소를 복구하고 현재 배송지와 마트를 유지한다")
    void rollsBackAllChangesWhenTransactionFails() {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            facade.deleteAddress(memberId, otherAddressId);
            addresses.flush();
            throw new IllegalStateException("검증용 후속 처리 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("검증용 후속 처리 실패");

        // then
        assertThat(addresses.existsById(otherAddressId)).isTrue();
        assertThat(addresses.countByMemberId(memberId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT selected_address_id FROM member WHERE id = ?", Long.class, memberId))
                .isEqualTo(addressId);
        assertThat(jdbc.queryForObject("SELECT mart_id FROM member WHERE id = ?", Long.class, memberId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT location_agreed_at FROM member WHERE id = ?",
                java.sql.Timestamp.class, memberId)).isNull();
    }

    @Test
    @DisplayName("같은 주소의 선택과 삭제가 경합해도 삭제된 주소가 현재 배송지가 되지 않는다")
    void keepsSelectionValidWhenDeleteRacesWithSelection() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<MockHttpServletResponse> first = executor.submit(() -> {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 선택 시작 대기 시간 초과");
            }
            return select(otherAddressId);
        });
        Future<MockHttpServletResponse> second = executor.submit(() -> {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 선택 시작 대기 시간 초과");
            }
            return deleteAddress(otherAddressId);
        });
        try {
            // when
            start.countDown();
            MockHttpServletResponse firstResponse = first.get(15, TimeUnit.SECONDS);
            MockHttpServletResponse secondResponse = second.get(15, TimeUnit.SECONDS);

            // then
            if (firstResponse.getStatus() == 200) {
                assertThat(secondResponse.getStatus()).isEqualTo(409);
                assertThat(mapper.readTree(secondResponse.getContentAsString()).path("code").asText())
                        .isEqualTo("ADDRESS-003");
                assertThat(addresses.existsById(otherAddressId)).isTrue();
            } else {
                assertThat(firstResponse.getStatus()).isEqualTo(404);
                assertThat(mapper.readTree(firstResponse.getContentAsString()).path("code").asText())
                        .isEqualTo("ADDRESS-001");
                assertThat(secondResponse.getStatus()).isEqualTo(204);
                assertThat(addresses.existsById(otherAddressId)).isFalse();
            }
            Long selectedId = jdbc.queryForObject(
                    "SELECT selected_address_id FROM member WHERE id = ?", Long.class, memberId);
            Long martId = jdbc.queryForObject("SELECT mart_id FROM member WHERE id = ?", Long.class, memberId);
            assertThat(selectedId).isIn(addressId, otherAddressId);
            assertThat(martId).isEqualTo(addressId.equals(selectedId) ? 1L : 2L);
            assertThat(addresses.existsById(selectedId)).isTrue();
            assertThat(selectedId).isEqualTo(firstResponse.getStatus() == 200 ? otherAddressId : addressId);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MockHttpServletResponse deleteAddress(Long targetId) throws Exception {
        return mockMvc.perform(delete("/api/members/me/addresses/{id}", targetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
    }

    private MockHttpServletResponse select(Long targetId) throws Exception {
        return mockMvc.perform(put("/api/members/me/addresses/{id}/select", targetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
    }
}
