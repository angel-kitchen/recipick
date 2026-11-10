package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.domain.member.facade.MembershipCancelFacade;
import com.recipick.global.security.JwtTokenProvider;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;

// 기존 결제 흐름 픽스처를 재사용해 회원 HTTP API부터 실제 C-5·DB까지 확인한다. 외부 PG만 HTTP 스텁이다.
class MembershipTerminateApiIntegrationTest extends MembershipRefundFlowSupport {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper mapper;
    @Autowired MembershipCancelFacade cancellations;

    MockHttpServletResponse terminate(Long id, String key) throws Exception {
        var request = post("/api/memberships/{id}/terminate", id)
                .header("Authorization", "Bearer " + testMemberTokens(member.getId()).accessToken());
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    JsonNode data(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    @Test
    @DisplayName("HTTP 해지 요청은 환불 완료와 이용권 종료 결과·계산 내역을 그대로 반환한다")
    void completesRefundAndTerminationThroughApi() throws Exception {
        // given
        Long id = renewedMembership();
        String paymentId = appliedPaymentId(id);
        clock.fixAt(DAY_10);
        String key = newKey();
        // when
        var result = data(terminate(id, key));
        // then
        assertThat(result.path("membershipId").asLong()).isEqualTo(id);
        assertThat(result.path("refundStatus").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("membershipTerminated").asBoolean()).isTrue();
        assertThat(result.path("reviewRequired").asBoolean()).isFalse();
        assertThat(result.path("paidAmount").asLong()).isEqualTo(3900);
        assertThat(result.path("periodDays").asInt()).isEqualTo(31);
        assertThat(result.path("usedDays").asInt()).isEqualTo(10);
        assertThat(result.path("usedDayFee").asLong()).isEqualTo(1258);
        assertThat(result.path("penaltyAmount").asLong()).isEqualTo(780);
        assertThat(result.path("refundAmount").asLong()).isEqualTo(1862);
        assertThat(result.path("requestedAt").asText()).endsWith("+09:00");
        assertThat(result.path("completedAt").asText()).endsWith("+09:00");
        assertThat(membership(id).getTerminatedAt()).isEqualTo(DAY_10);
        assertThat(data(terminate(id, key.toUpperCase(Locale.ROOT)))).isEqualTo(result);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(refundsOfMember()).hasSize(1);
    }

    @Test
    @DisplayName("유예 중 0원 환불도 API에서 완료·종료를 반환하며 PG는 호출하지 않는다")
    void completesZeroRefundWithoutPg() throws Exception {
        // given
        Long id = activeMembership();
        simulateFirstPayment(id);
        clock.fixAt(T.plusHours(1));
        // when
        var result = data(terminate(id, newKey()));
        // then
        assertThat(result.path("refundStatus").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("refundAmount").asLong()).isZero();
        assertThat(result.path("membershipTerminated").asBoolean()).isTrue();
        assertThat(PORTONE.totalCancelCount()).isZero();
    }

    @Test
    @DisplayName("PG 결과 불명은 200 REQUESTED이며 회원 API가 이용권을 별도로 종료하지 않는다")
    void keepsMembershipWhenRequested() throws Exception {
        // given
        Long id = renewedMembership();
        PORTONE.getPaymentServerError(appliedPaymentId(id));
        clock.fixAt(DAY_10);
        String key = newKey();
        // when
        var result = data(terminate(id, key));
        // then
        assertThat(result.path("refundStatus").asText()).isEqualTo("REQUESTED");
        assertThat(result.path("membershipTerminated").asBoolean()).isFalse();
        assertThat(result.path("completedAt").isNull()).isTrue();
        assertThat(membership(id).getTerminatedAt()).isNull();
        var refused = terminate(id, newKey());
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(refused.getContentAsString()).contains("MEMBERSHIP-010");
        assertThat(refundsOfMember()).hasSize(1);
    }

    @Test
    @DisplayName("명확한 환불 실패는 200 FAILED로 전달하고 이용권을 유지한다")
    void returnsFailedAsBusinessStatus() throws Exception {
        // given
        Long id = renewedMembership();
        PORTONE.simulatePostStatus(appliedPaymentId(id), "FAILED");
        clock.fixAt(DAY_10);
        // when
        var result = data(terminate(id, newKey()));
        // then
        assertThat(result.path("refundStatus").asText()).isEqualTo("FAILED");
        assertThat(result.path("membershipTerminated").asBoolean()).isFalse();
        assertThat(membership(id).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("운영 확인이 필요한 환불은 200 응답의 reviewRequired로 전달한다")
    void returnsReviewRequired() throws Exception {
        // given
        Long id = renewedMembership();
        PORTONE.cancelError(appliedPaymentId(id), 403, "FORBIDDEN");
        clock.fixAt(DAY_10);
        // when
        var result = data(terminate(id, newKey()));
        // then
        assertThat(result.path("refundStatus").asText()).isEqualTo("REQUESTED");
        assertThat(result.path("reviewRequired").asBoolean()).isTrue();
        assertThat(result.path("membershipTerminated").asBoolean()).isFalse();
        assertThat(membership(id).getTerminatedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"missing,COMMON-007", "blank,COMMON-007", "long,COMMON-001", "space,COMMON-001"})
    @DisplayName("헤더 누락·잘못된 멱등 키는 기존 결제 오류 계약으로 반환한다")
    void rejectsInvalidKeys(String kind, String code) throws Exception {
        // given
        String key = switch (kind) {
            case "missing" -> null;
            case "blank" -> " ";
            case "long" -> "x".repeat(65);
            default -> "key with space";
        };
        // when
        var response = terminate(1L, key);
        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains(code);
        assertThat(refundsOfMember()).isEmpty();
        assertThat(PORTONE.totalCancelCount()).isZero();
    }

    @Test
    @DisplayName("같은 키로 다른 이용권을 지정하면 COMMON-011을 반환한다")
    void rejectsChangedMembershipOnSameKey() throws Exception {
        // given
        Long id = activeMembership();
        simulateFirstPayment(id);
        clock.fixAt(T.plusHours(1));
        String key = newKey();
        data(terminate(id, key));
        // when
        var response = terminate(id + 1000000, key);
        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("COMMON-011");
        assertThat(refundsOfMember()).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않거나 타인의 이용권은 환불하지 않는다")
    void rejectsOtherMembersMembership() throws Exception {
        // given
        Long id = activeMembership();
        var other = saveMember();
        var request = post("/api/memberships/{id}/terminate", id)
                .header("Authorization", "Bearer " + testMemberTokens(other.getId()).accessToken())
                .header("Idempotency-Key", newKey());
        // when
        var response = mvc.perform(request).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("MEMBERSHIP-006");
        assertThat(refundsOfMember()).isEmpty();
    }

    @Test
    @DisplayName("바깥 트랜잭션의 호출은 환불 접수 전에 거절한다")
    void rejectsOuterTransaction() {
        // given, when, then
        assertThatThrownBy(() -> transactionTemplate.execute(status -> cancellations.terminate(member.getId(), 1L, newKey())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(refundsOfMember()).isEmpty();
    }

    @Test
    @DisplayName("미인증 요청은 즉시 해지 API를 호출할 수 없다")
    void requiresAuthentication() throws Exception {
        // given, when
        var response = mvc.perform(post("/api/memberships/1/terminate")
                .header("Idempotency-Key", newKey())).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(refundsOfMember()).isEmpty();
    }

    @Test
    @DisplayName("양수가 아닌 이용권 ID는 입력 오류로 거절한다")
    void rejectsNonpositiveId() throws Exception {
        // given, when
        var response = terminate(0L, newKey());
        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("COMMON-001");
        assertThat(refundsOfMember()).isEmpty();
    }
}
