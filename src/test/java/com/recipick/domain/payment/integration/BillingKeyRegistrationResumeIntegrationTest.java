package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.payment.dto.request.BillingKeyRegisterRequest;
import com.recipick.domain.payment.entity.BillingKeyIssueRequest;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.facade.BillingKeyRegistrationFacade;
import com.recipick.domain.payment.repository.BillingKeyIssueRequestRepository;
import com.recipick.domain.payment.service.BillingKeyFingerprint;
import com.recipick.domain.payment.service.BillingKeyIssueRequestService;
import com.recipick.global.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;

// 카드 등록 API(POST /api/billing-keys)와 카드 대기 갱신 회차 재개의 연결. 실제 서블릿 스택·MySQL·PG 스텁(빌링키 조회·청구)을 탄다.
// 등록 TX가 커밋된 뒤에만 재개가 돌고, 재개가 어디서 실패하든 등록 응답은 성공이어야 한다. 조회 단계의 실패는 Mockito 없이
// 실제 DB에 장애를 넣어 재현한다 — 회차 테이블 이름을 잠시 바꿔 그 조회만 실패시키고 finally로 되돌린다(등록 TX는 이 테이블을 쓰지 않는다)
class BillingKeyRegistrationResumeIntegrationTest extends MembershipRenewalFlowSupport {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired BillingKeyIssueRequestService issueRequests;
    @Autowired BillingKeyIssueRequestRepository issueRequestRepository;
    @Autowired BillingKeyRegistrationFacade registrationFacade;
    @Autowired BillingKeyFingerprint fingerprint;

    @AfterEach
    void cleanUpIssueRequests() {
        jdbc.update("DELETE FROM billing_key_issue_request WHERE member_id = ?", member.getId());
    }

    @Test
    @DisplayName("카드 대기 회차가 있는 회원이 카드를 등록하면 200과 함께 같은 회차에서 한 번 청구되고 이용기간이 연장된다")
    void registerResumesWaitingRenewal() throws Exception {
        // given
        Long membershipId = activeMembership();
        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.REPLACED);
        renewalFacade.runOnce();
        MembershipPayment waiting = onlyRenewalPayment(membershipId);
        assertThat(waiting.getStatus()).isEqualTo(MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED);
        String newKey = "billing-key-test-" + UUID.randomUUID();
        String issueId = issued(newKey);
        PORTONE.defaultPaid(FEE, T);

        // when
        mvc.perform(post("/api/billing-keys")
                .header("Authorization", "Bearer " + tokens.createMemberTokens(member.getId()).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueId\":\"" + issueId + "\",\"billingKey\":\"" + newKey + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // then
        MembershipPayment resumed = payments.findById(waiting.getId()).orElseThrow();
        assertThat(resumed.getStatus()).isEqualTo(MembershipPaymentStatus.PAID);
        assertThat(attemptsOf(waiting.getId())).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        assertThat(renewalPaymentIds(membershipId)).hasSize(1);
    }

    @Test
    @DisplayName("재개 전 회차 목록 조회가 실패해도 카드 등록은 커밋되고 200이다. 회차는 카드 대기로 남아 다음 실행이 재개한다")
    void registrationSucceedsWhenResumeLookupFails() throws Exception {
        // given
        Long membershipId = activeMembership();
        time.billingKeyStatus(cardA.getId(), BillingKeyStatus.REPLACED);
        renewalFacade.runOnce();
        MembershipPayment waiting = onlyRenewalPayment(membershipId);
        String newKey = "billing-key-test-" + UUID.randomUUID();
        String issueId = issued(newKey);
        PORTONE.defaultPaid(FEE, T);
        // DDL은 암묵적 커밋이 있어 업무 TX 밖·별도 연결에서 실행한다. 되돌리기 전까지 회차 조회는 모두 실패한다
        jdbc.execute("RENAME TABLE membership_payment TO membership_payment_fault");
        try {
            // when
            mvc.perform(post("/api/billing-keys")
                    .header("Authorization", "Bearer " + tokens.createMemberTokens(member.getId()).accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"issueId\":\"" + issueId + "\",\"billingKey\":\"" + newKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        } finally {
            jdbc.execute("RENAME TABLE membership_payment_fault TO membership_payment");
        }
        MembershipPaymentStatus afterRegistration = payments.findById(waiting.getId()).orElseThrow().getStatus();
        renewalFacade.runOnce();

        // then
        assertThat(jdbc.queryForObject("SELECT status FROM billing_key WHERE portone_billing_key_hash = ?",
            String.class, fingerprint.of(newKey))).isEqualTo("ACTIVE");
        assertThat(afterRegistration).isEqualTo(MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED);
        assertThat(payments.findById(waiting.getId()).orElseThrow().getStatus())
            .isEqualTo(MembershipPaymentStatus.PAID);
        assertThat(attemptsOf(waiting.getId())).hasSize(1);
        assertThat(PORTONE.totalPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("카드 등록은 바깥 트랜잭션 안에서 부르면 PG 조회 전에 거절된다(실제 프록시). 등록 행·PG 요청 0")
    void registrationRejectsOuterTransactionBeforePg() {
        // given
        String newKey = "billing-key-test-" + UUID.randomUUID();
        String issueId = issued(newKey);

        // when / then
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
            s -> registrationFacade.register(member.getId(), new BillingKeyRegisterRequest(issueId, newKey))))
            .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE portone_billing_key_hash = ?",
            Long.class, fingerprint.of(newKey))).isZero();
        assertThat(PORTONE.totalRequests()).isZero();
    }

    // 발급 요청을 만들고 PG 스텁에 그 요청과 묶인 ISSUED 카드 응답을 둔다. 채널은 카드 등록 전용 키 — 검증기가 대조하는 키다
    private String issued(String billingKey) {
        BillingKeyIssueRequest request = issueRequests.create(member.getId());
        PORTONE.billingKeyIssued(billingKey, "test-store-id", "test-billing-channel-key", request.getIssueId(),
            member.getId(), T.minusMinutes(1));
        return request.getIssueId();
    }
}
