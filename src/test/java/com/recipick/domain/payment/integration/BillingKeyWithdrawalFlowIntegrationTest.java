package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.domain.payment.facade.BillingKeyDeletionExecutionFacade;
import com.recipick.domain.payment.repository.BillingKeyDeletionRepository;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.infra.portone.PortOneBillingKeyDeletionClient;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

// 회원 HTTP API·실제 네 Scope·카드 삭제 요청 커밋을 연결하며 외부 PG만 로컬 HTTP 스텁이다.
@Import(BillingKeyWithdrawalFlowIntegrationTest.DeletionHttpConfig.class)
class BillingKeyWithdrawalFlowIntegrationTest extends MembershipRenewalFlowSupport {
    private static final BillingKeyDeletionStubServer KEY_PG = new BillingKeyDeletionStubServer();

    @DynamicPropertySource
    static void deletionProperties(DynamicPropertyRegistry registry) {
        registry.add("payment.billing-key-deletion.enabled", () -> "true");
        registry.add("payment.billing-key-deletion.withdrawal-enabled", () -> "true");
        registry.add("portone.store-id", () -> BillingKeyDeletionStubServer.STORE);
    }

    @TestConfiguration
    static class DeletionHttpConfig {
        @Bean
        @Primary
        PortOneBillingKeyDeletionClient deletionTestHttpClient() {
            var factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(15));
            return new PortOneBillingKeyDeletionClient(RestClient.builder().requestFactory(factory).build(),
                    KEY_PG.baseUrl());
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider tokens;
    @Autowired BillingKeyDeletionRepository deletions;
    @Autowired BillingKeyDeletionExecutionFacade deletionExecutor;
    String token;
    BillingKeyDeletionStubServer.Entry remote;

    @BeforeEach
    void setUpWithdrawal() {
        KEY_PG.reset();
        clock.fixAt(T);
        remote = KEY_PG.register(cardA.getPortoneBillingKey());
        token = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @AfterEach
    void cleanupWithdrawalRows() {
        KEY_PG.releaseDeleteReply();
        if (!workersTerminated) {
            return;
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE d FROM billing_key_deletion d JOIN billing_key b ON d.billing_key_id = b.id WHERE b.member_id = ?",
                    memberId);
            jdbc.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        }
    }

    MockHttpServletResponse withdraw(String password, long balance) throws Exception {
        return mvc.perform(post("/api/members/me/withdraw").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of(
                        "password", password, "acknowledgedPointBalance", balance))))
                .andReturn().getResponse();
    }

    void assertNotWithdrawn() {
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = ?",
                Boolean.class, member.getId())).isTrue();
    }

    long deletionId() {
        return deletions.findIdByBillingKeyId(cardA.getId()).orElseThrow();
    }

    @Test
    @DisplayName("대기 오류를 반환해도 카드 삭제 요청은 커밋되고 삭제 확인 뒤 재요청하면 실제 탈퇴한다")
    void commitsPreparationBeforePendingResponseAndWithdrawsAfterDeletion() throws Exception {
        // given, when
        var pending = withdraw(PASSWORD, 0);
        // then
        assertThat(pending.getStatus()).isEqualTo(409);
        assertThat(pending.getContentAsString()).contains("BILLING-030");
        assertThat(billingKeys.findById(cardA.getId()).orElseThrow().getStatus().name()).isEqualTo("DELETE_REQUESTED");
        long id = deletionId();
        assertNotWithdrawn();
        assertThat(KEY_PG.getCount()).isZero();
        assertThat(KEY_PG.deleteCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_point WHERE member_id = ?",
                Long.class, member.getId())).isZero();
        // when — 외부 정리 Worker의 실제 실행 후 회원이 다시 요청한다.
        deletionExecutor.execute(id);
        var completed = withdraw(PASSWORD, 0);
        // then
        assertThat(completed.getStatus()).isEqualTo(204);
        assertThat(completed.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NOT NULL FROM member WHERE id = ?",
                Boolean.class, member.getId())).isTrue();
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
        assertThat(mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("삭제 대기 중 재요청은 기존 카드 정리 접수를 재사용하며 회원을 파기하지 않는다")
    void repeatsPendingWithoutDuplicateRequests() throws Exception {
        // given
        withdraw(PASSWORD, 0);
        long id = deletionId();
        // when
        var repeated = withdraw(PASSWORD, 0);
        // then
        assertThat(repeated.getStatus()).isEqualTo(409);
        assertThat(repeated.getContentAsString()).contains("BILLING-030");
        assertThat(deletionId()).isEqualTo(id);
        assertNotWithdrawn();
        assertThat(KEY_PG.deleteCount()).isZero();
    }

    @Test
    @DisplayName("동시 탈퇴 요청은 카드 삭제 접수를 한 건만 만들고 두 요청 모두 정리 대기를 반환한다")
    void concurrentWithdrawalsShareOneCleanupRequest() throws Exception {
        // given
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        workersTerminated = false;
        java.util.concurrent.Callable<MockHttpServletResponse> attempt = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 탈퇴 요청 대기 시간 초과");
            }
            return withdraw(PASSWORD, 0);
        };
        try {
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            // when
            start.countDown();
            var firstResponse = first.get(20, TimeUnit.SECONDS);
            var secondResponse = second.get(20, TimeUnit.SECONDS);
            // then
            assertThat(firstResponse.getStatus()).isEqualTo(409);
            assertThat(firstResponse.getContentAsString()).contains("BILLING-030");
            assertThat(secondResponse.getStatus()).isEqualTo(409);
            assertThat(secondResponse.getContentAsString()).contains("BILLING-030");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_key_deletion WHERE billing_key_id = ?",
                    Long.class, cardA.getId())).isEqualTo(1);
            assertNotWithdrawn();
            assertThat(KEY_PG.getCount()).isZero();
            assertThat(KEY_PG.deleteCount()).isZero();
        } finally {
            start.countDown();
            pool.shutdownNow();
            workersTerminated = pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        assertThat(workersTerminated).isTrue();
    }

    @Test
    @DisplayName("원격 정리에 운영 확인이 필요하면 BILLING-031을 반환하고 탈퇴하지 않는다")
    void rejectsReviewRequired() throws Exception {
        // given
        withdraw(PASSWORD, 0);
        remote.getHttp = 403;
        remote.errorType = "FORBIDDEN";
        deletionExecutor.execute(deletionId());
        // when
        var response = withdraw(PASSWORD, 0);
        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("BILLING-031");
        assertNotWithdrawn();
    }

    @Test
    @DisplayName("잘못된 비밀번호는 카드 삭제 접수보다 먼저 거절한다")
    void rejectsPasswordBeforeDeletingCards() throws Exception {
        // given, when
        var response = withdraw("Wrong1!", 0);
        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MEMBER-005");
        assertThat(deletions.findIdByBillingKeyId(cardA.getId())).isEmpty();
        assertNotWithdrawn();
    }

    @Test
    @DisplayName("활성 이용권이 있으면 카드 정리를 시작하지 않는다")
    void rejectsMembershipBeforeDeletingCards() throws Exception {
        // given
        activeMembership();
        clock.fixAt(T.minusDays(1));
        token = tokens.createMemberTokens(member.getId()).accessToken();
        // when
        var response = withdraw(PASSWORD, 0);
        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("MEMBER-007");
        assertThat(deletions.findIdByBillingKeyId(cardA.getId())).isEmpty();
        assertNotWithdrawn();
    }

    @Test
    @DisplayName("카드가 없는 회원은 실제 정산 검사 후 바로 탈퇴한다")
    void withdrawsWithoutStoredCards() throws Exception {
        // given
        billingKeys.deleteById(cardA.getId());
        // when
        var response = withdraw(PASSWORD, 0);
        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(KEY_PG.getCount()).isZero();
    }

    @Test
    @DisplayName("카드 정리가 끝났어도 최종 포인트 잔액 검사가 실패하면 회원은 유지하고 원격 삭제는 되돌리지 않는다")
    void preservesCleanupWhenFinalBalanceCheckFails() throws Exception {
        // given
        withdraw(PASSWORD, 0);
        deletionExecutor.execute(deletionId());
        // when
        var response = withdraw(PASSWORD, 1);
        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("POINT-002");
        assertNotWithdrawn();
        assertThat(billingKeys.findById(cardA.getId()).orElseThrow().getStatus().name()).isEqualTo("DELETED");
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
    }
}
