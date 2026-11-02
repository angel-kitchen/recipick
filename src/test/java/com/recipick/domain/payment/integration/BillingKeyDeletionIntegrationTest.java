package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.member.dto.request.MemberWithdrawRequest;
import com.recipick.domain.member.facade.MemberWithdrawalProcessor;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.BillingKeyDeletion;
import com.recipick.domain.payment.entity.enums.BillingKeyDeletionStatus;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.exception.BillingKeyDeletionErrorCode;
import com.recipick.domain.payment.facade.BillingKeyDeletionExecutionFacade;
import com.recipick.domain.payment.facade.BillingKeyDeletionProcessor;
import com.recipick.domain.payment.facade.BillingKeyDeletionRecoveryFacade;
import com.recipick.domain.payment.facade.BillingKeyDeletionRequestProcessor;
import com.recipick.domain.payment.repository.BillingKeyDeletionRepository;
import com.recipick.domain.payment.service.BillingKeyDeletionCode;
import com.recipick.domain.payment.service.BillingKeyDeletionExecutionResult;
import com.recipick.domain.payment.service.BillingKeyDeletionTicket;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOneBillingKeyDeletionClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

// 실제 회원·결제 엔티티와 MySQL을 쓰고 빌링키 삭제 HTTP만 별도 로컬 스텁으로 바꾼다. 기존 PortOneStubServer·IntegrationTestSupport는 건드리지 않는다.
// 탈퇴 API 전체 흐름(준비 TX → 최종 탈퇴)은 회원 Facade 연결과 함께 BillingKeyWithdrawalFlowIntegrationTest가 본다 — 여기서는
// 탈퇴 최종 검사(AUTO_BILLING Checker)까지만 확인한다
@Import(BillingKeyDeletionIntegrationTest.DeletionHttpConfig.class)
class BillingKeyDeletionIntegrationTest extends MembershipRenewalFlowSupport {
    private static final BillingKeyDeletionStubServer KEY_PG = new BillingKeyDeletionStubServer();
    private static final Duration WAIT = Duration.ofSeconds(20);

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
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(15));
            return new PortOneBillingKeyDeletionClient(
                RestClient.builder().requestFactory(factory).build(), KEY_PG.baseUrl());
        }
    }

    @Autowired BillingKeyDeletionRequestProcessor deletionRequests;
    @Autowired BillingKeyDeletionProcessor deletionProcessor;
    @Autowired BillingKeyDeletionExecutionFacade deletionExecutor;
    @Autowired BillingKeyDeletionRecoveryFacade deletionRecovery;
    @Autowired BillingKeyDeletionRepository deletions;
    @Autowired MemberService memberService;
    @Autowired MemberWithdrawalProcessor withdrawalProcessor;

    private BillingKeyDeletionStubServer.Entry remote;

    @BeforeEach
    void resetDeletionServer() {
        KEY_PG.reset();
        remote = KEY_PG.register(cardA.getPortoneBillingKey());
        clock.fixAt(T);
    }

    @AfterEach
    void removeDeletionRowsBeforeParentRemovesKeys() {
        KEY_PG.releaseDeleteReply();
        if (!workersTerminated) {
            return;
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE d FROM billing_key_deletion d JOIN billing_key b ON d.billing_key_id = b.id "
                + "WHERE b.member_id = ?", memberId);
            // 실제 탈퇴의 POINTS Checker가 만든 0원 잔액 행도 정리한다.
            jdbc.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("ACTIVE 키는 교체 키 스캔이 건드리지 않고, 교체 키 접수는 PG 없이 먼저 커밋한다")
    void activeKeyIsNotCollectedAndReplacedRequestCommitsBeforeHttp() {
        assertThat(deletionRecovery.requestReplacedOnce()).isZero();
        assertThat(deletionRequests.requestReplaced(member.getId(), cardA.getId())).isNull();
        Long id = requestReplaced();
        assertThat(id).isNotNull();
        assertThat(key().getStatus()).isEqualTo(BillingKeyStatus.DELETE_REQUESTED);
        assertThat(task(id).getStatus()).isEqualTo(BillingKeyDeletionStatus.REQUESTED);
        assertThat(KEY_PG.getCount()).isZero();
        assertThat(KEY_PG.deleteCount()).isZero();
    }

    @Test
    @DisplayName("원격 삭제 확인 후 로컬 키와 작업을 함께 완료하고, 같은 작업 재실행은 추가 DELETE가 없다")
    void confirmsDeletionOnceAndPreservesLocalKeyRow() {
        Long id = requestReplaced();
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.COMPLETED);
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.SKIPPED);
        assertThat(key().getStatus()).isEqualTo(BillingKeyStatus.DELETED);
        assertThat(key().getDeletedAt()).isEqualTo(T);
        assertThat(task(id).getCompletedAt()).isEqualTo(T);
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
        assertThat(billingKeys.existsById(cardA.getId())).isTrue();
    }

    @Test
    @DisplayName("미해결 READY가 사용하는 REPLACED 키는 원격 삭제를 접수하지 않는다")
    void unresolvedAttemptProtectsReplacedKey() {
        Long membershipId = activeMembership();
        renewalProcessor.accept(member.getId(), membershipId);
        replaceLocalKey();
        assertThat(deletionRequests.requestReplaced(member.getId(), cardA.getId())).isNull();
        assertThat(key().getStatus()).isEqualTo(BillingKeyStatus.REPLACED);
        assertThat(deletions.findIdByBillingKeyId(cardA.getId())).isEmpty();
        assertThat(KEY_PG.deleteCount()).isZero();
    }

    @Test
    @DisplayName("DELETE 성공 응답만으로 완료하지 않으며 다음 조회 시각 전에는 HTTP를 반복하지 않는다")
    void acknowledgementAloneDoesNotCompleteAndDueTimeIsEnforced() {
        Long id = requestReplaced();
        remote.applyDelete = false;
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.PENDING);
        int gets = KEY_PG.getCount();
        assertThat(key().getStatus()).isEqualTo(BillingKeyStatus.DELETE_REQUESTED);
        clock.fixAt(T.plusSeconds(59));
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.SKIPPED);
        assertThat(KEY_PG.getCount()).isEqualTo(gets);
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
        clock.fixAt(T.plusMinutes(1));
        remote.applyDelete = true;
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.COMPLETED);
        assertThat(KEY_PG.deleteCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("최초 조회가 404뿐이면 상점 근거 없이 삭제 완료로 추정하지 않는다")
    void firstNotFoundCannotProveStoreOwnership() {
        Long id = requestReplaced();
        remote.getHttp = 404;
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.REVIEW_REQUIRED);
        assertThat(task(id).getLastCode()).isEqualTo(BillingKeyDeletionCode.SCOPE_UNPROVEN);
        assertThat(key().getStatus()).isEqualTo(BillingKeyStatus.DELETE_REQUESTED);
        assertThat(KEY_PG.deleteCount()).isZero();
    }

    @Test
    @DisplayName("동일 키·상점을 먼저 검증한 뒤의 정확한 404는 원격 삭제 근거가 된다")
    void exactNotFoundAfterVerifiedIdentityCompletes() {
        Long id = requestReplaced();
        remote.disappearAfterDelete = true;
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.COMPLETED);
        assertThat(task(id).getIdentityVerifiedAt()).isEqualTo(T);
        assertThat(task(id).getLastCode()).isEqualTo(BillingKeyDeletionCode.BILLING_KEY_NOT_FOUND);
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실연동 채널과 키 식별 불일치는 자동 삭제를 중단한다")
    void liveChannelAndIdentityMismatchAreNeverDeleted() {
        Long id = requestReplaced();
        remote.channelType = "LIVE";
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.REVIEW_REQUIRED);
        assertThat(task(id).getLastCode()).isEqualTo(BillingKeyDeletionCode.CHANNEL_NOT_TEST);
        remote.channelType = "TEST";
        remote.responseKey = "not-the-requested-key";
        clock.fixAt(T.plusHours(1));
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.REVIEW_REQUIRED);
        assertThat(KEY_PG.deleteCount()).isZero();
    }

    @Test
    @DisplayName("운영 확인으로 전환되면 DELETE는 멈추지만 정확한 삭제 GET은 여전히 반영한다")
    void reviewOnlyRechecksAndAcceptsVerifiedRemoteDeletion() {
        Long id = requestReplaced();
        remote.deleteHttp = 403;
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.REVIEW_REQUIRED);
        assertThat(task(id).getLastCode()).isEqualTo(BillingKeyDeletionCode.AUTH_ERROR);
        remote.deleteHttp = 200;
        clock.fixAt(T.plusHours(1));
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.REVIEW_REQUIRED);
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
        remote.deleted = true;
        clock.fixAt(T.plusHours(2));
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.COMPLETED);
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PG 삭제 후 DB 완료 저장이 실패하면 같은 작업의 GET으로 복구하고 DELETE를 재전송하지 않는다")
    void databaseFailureAfterRemoteDeletionRollsBackBothLocalStates() {
        Long id = requestReplaced();
        String constraint = "ck_key_deletion_finish_test";
        // MySQL은 CHECK가 AUTO_INCREMENT 열(id)을 참조하지 못한다 — 키 FK 열로 이 행만 겨눈다
        jdbc.execute("ALTER TABLE billing_key_deletion ADD CONSTRAINT " + constraint
            + " CHECK (billing_key_id <> " + cardA.getId() + " OR status <> 'COMPLETED')");
        try {
            assertThatThrownBy(() -> deletionExecutor.execute(id)).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("ALTER TABLE billing_key_deletion DROP CHECK " + constraint);
        }
        assertThat(key().getStatus()).isEqualTo(BillingKeyStatus.DELETE_REQUESTED);
        assertThat(task(id).getStatus()).isEqualTo(BillingKeyDeletionStatus.REQUESTED);
        clock.fixAt(T.plusMinutes(6));
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.COMPLETED);
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("임대만 만료되고 새 토큰이 아직 없어도 옛 실행자의 예약과 발송 준비는 거절한다")
    void expiredOwnerCannotScheduleOrDispatch() {
        Long id = requestReplaced();
        BillingKeyDeletionTicket ticket = deletionProcessor.claim(deletions.findRef(id).orElseThrow()).orElseThrow();
        clock.fixAt(T.plusMinutes(5));
        assertThat(deletionProcessor.defer(ticket, BillingKeyDeletionCode.HTTP_ERROR)).isFalse();
        assertThat(deletionProcessor.prepareDelete(ticket)).isFalse();
        assertThat(task(id).getClaimToken()).isEqualTo(ticket.token());
        assertThat(task(id).getFirstDispatchedAt()).isNull();
        assertThat(KEY_PG.deleteCount()).isZero();
    }

    @Test
    @DisplayName("다섯 실행자가 같은 요청을 집어도 정상 임대 안에서는 DELETE 한 번만 보낸다")
    void concurrentExecutorsDeleteOnce() throws Exception {
        Long id = requestReplaced();
        CountDownLatch arrived = KEY_PG.holdDeleteReply();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        workersTerminated = false;
        List<Future<BillingKeyDeletionExecutionResult>> futures = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < 5; i++) {
                futures.add(pool.submit(() -> {
                    if (!start.await(WAIT.toSeconds(), TimeUnit.SECONDS)) {
                        throw new IllegalStateException("출발 신호 없음");
                    }
                    return deletionExecutor.execute(id);
                }));
            }
            start.countDown();
            assertThat(arrived.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(KEY_PG.deleteCount()).isEqualTo(1);
            KEY_PG.releaseDeleteReply();
            for (Future<BillingKeyDeletionExecutionResult> future : futures) {
                future.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            }
        } finally {
            KEY_PG.releaseDeleteReply();
            pool.shutdownNow();
            workersTerminated = pool.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
        assertThat(workersTerminated).isTrue();
        assertThat(task(id).getStatus()).isEqualTo(BillingKeyDeletionStatus.COMPLETED);
        assertThat(KEY_PG.deleteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DB 복구는 등록된 삭제 요청을 찾고 완료 후에는 다시 처리하지 않는다")
    void databaseRecoveryRunsWithoutRedisSignal() {
        Long id = requestReplaced();
        assertThat(deletionRecovery.recoverOnce()).isEqualTo(1);
        assertThat(task(id).getStatus()).isEqualTo(BillingKeyDeletionStatus.COMPLETED);
        assertThat(deletionRecovery.recoverOnce()).isZero();
    }

    @Test
    @DisplayName("재활성화가 먼저면 삭제를 접수하지 않고, 삭제 접수가 먼저면 재활성화할 수 없다")
    void reactivationAndDeletionAreIrreversibleInBothOrders() {
        replaceLocalKey();
        transactionTemplate.executeWithoutResult(s -> {
            memberService.lockActiveMember(member.getId());
            billingKeys.findByIdForUpdate(cardA.getId()).orElseThrow().reactivate();
        });
        assertThat(deletionRequests.requestReplaced(member.getId(), cardA.getId())).isNull();
        Long id = requestReplaced();
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s -> {
            memberService.lockActiveMember(member.getId());
            billingKeys.findByIdForUpdate(cardA.getId()).orElseThrow().reactivate();
        })).isInstanceOf(IllegalStateException.class);
        assertThat(task(id).getStatus()).isEqualTo(BillingKeyDeletionStatus.REQUESTED);
    }

    @Test
    @DisplayName("삭제가 확인되지 않은 키가 있으면 최종 탈퇴 검사가 409로 거절하고, 키가 DELETED로 확인된 뒤에는 탈퇴한다")
    void finalWithdrawalCheckRequiresEveryKeyConfirmedDeleted() {
        // given
        MemberWithdrawRequest request = new MemberWithdrawRequest(PASSWORD, 0L);

        // when, then — ACTIVE 키가 남아 있다(탈퇴 준비가 접수하기 전 상태)
        assertThatThrownBy(() -> withdrawalProcessor.withdraw(member.getId(), request))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(BillingKeyDeletionErrorCode.CLEANUP_PENDING));
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = ?",
            Boolean.class, member.getId())).isTrue();
        assertThat(KEY_PG.deleteCount()).isZero();

        // when — 키가 원격 삭제 확인까지 끝난다
        Long id = requestReplaced();
        assertThat(deletionExecutor.execute(id)).isEqualTo(BillingKeyDeletionExecutionResult.COMPLETED);
        withdrawalProcessor.withdraw(member.getId(), request);

        // then
        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NOT NULL FROM member WHERE id = ?",
            Boolean.class, member.getId())).isTrue();
    }

    private Long requestReplaced() {
        replaceLocalKey();
        return deletionRequests.requestReplaced(member.getId(), cardA.getId());
    }

    private void replaceLocalKey() {
        transactionTemplate.executeWithoutResult(s -> {
            memberService.lockActiveMember(member.getId());
            billingKeys.findByIdForUpdate(cardA.getId()).orElseThrow().replace();
        });
    }

    private BillingKey key() {
        return billingKeys.findById(cardA.getId()).orElseThrow();
    }

    private BillingKeyDeletion task(Long id) {
        return deletions.findById(id).orElseThrow();
    }
}
