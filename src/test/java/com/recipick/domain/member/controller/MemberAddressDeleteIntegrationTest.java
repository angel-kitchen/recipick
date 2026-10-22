package com.recipick.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.MemberAddress;
import com.recipick.domain.member.repository.MemberAddressRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberAddressDeleteIntegrationTest extends IntegrationTestSupport {

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

    private Member member;
    private String token;
    private MemberAddress selected;
    private MemberAddress target;

    @BeforeEach
    void setUp() {
        member = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@as.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).name("배송지 선택 검증")
                .passwordHash("test-password-hash").build());
        token = tokens.createMemberTokens(member.getId()).accessToken();
        selected = address(member, "1111014000");
        target = address(member, "4121010300");
        member.selectAddress(selected, 1L);
        members.flush();
    }

    @Test
    @DisplayName("선택하지 않은 본인 주소를 삭제하면 본문 없는 204이고 나머지 주소와 마트는 유지한다")
    void deletesOnlyOwnedUnselectedAddress() throws Exception {
        // given
        Long selectedId = selected.getId();

        // when
        MockHttpServletResponse response = deleteAddress(target.getId(), token);
        addresses.flush();

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(addresses.existsById(target.getId())).isFalse();
        assertThat(addresses.existsById(selectedId)).isTrue();
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(1);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(selectedId);
        assertThat(member.getMartId()).isEqualTo(1L);
        assertThat(member.getLocationAgreedAt()).isNull();
    }

    @Test
    @DisplayName("현재 배송지 삭제는 ADDRESS-003으로 거절하고 모든 주소를 유지한다")
    void rejectsSelectedAddress() throws Exception {
        // given
        Long selectedId = selected.getId();

        // when
        MockHttpServletResponse response = deleteAddress(selectedId, token);

        // then
        assertError(response, 409, "ADDRESS-003");
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(2);
        assertThat(member.getSelectedAddress().getId()).isEqualTo(selectedId);
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이용 마트가 없는 현재 배송지도 삭제할 수 없다")
    void protectsSelectedAddressWithoutMart() throws Exception {
        // given
        member.selectAddress(selected, null);
        members.flush();

        // when
        MockHttpServletResponse response = deleteAddress(selected.getId(), token);

        // then
        assertError(response, 409, "ADDRESS-003");
        assertThat(addresses.existsById(selected.getId())).isTrue();
        assertThat(member.getMartId()).isNull();
    }

    @Test
    @DisplayName("없는 주소와 다른 회원 주소는 동일한 ADDRESS-001로 응답한다")
    void hidesUnownedAddress() throws Exception {
        // given
        Member other = members.saveAndFlush(Member.builder().email(UUID.randomUUID() + "@other.test")
                .phone(UUID.randomUUID().toString().substring(0, 20)).passwordHash("hash").build());
        MemberAddress foreign = address(other, "1111014000");

        // when
        MockHttpServletResponse missing = deleteAddress(Long.MAX_VALUE, token);
        MockHttpServletResponse unowned = deleteAddress(foreign.getId(), token);

        // then
        assertError(missing, 404, "ADDRESS-001");
        assertError(unowned, 404, "ADDRESS-001");
        assertThat(addresses.existsById(foreign.getId())).isTrue();
        assertThat(addresses.countByMemberId(member.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 삭제한 주소를 다시 삭제하면 ADDRESS-001을 반환한다")
    void returnsNotFoundWhenAlreadyDeleted() throws Exception {
        // given
        assertThat(deleteAddress(target.getId(), token).getStatus()).isEqualTo(204);

        // when
        MockHttpServletResponse response = deleteAddress(target.getId(), token);

        // then
        assertError(response, 404, "ADDRESS-001");
        assertThat(member.getSelectedAddress().getId()).isEqualTo(selected.getId());
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 주소를 먼저 선택하면 이전 현재 배송지를 삭제할 수 있다")
    void allowsDeletingPreviouslySelectedAddress() throws Exception {
        // given
        member.selectAddress(target, 2L);
        members.flush();

        // when
        MockHttpServletResponse response = deleteAddress(selected.getId(), token);

        // then
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(addresses.existsById(selected.getId())).isFalse();
        assertThat(member.getSelectedAddress().getId()).isEqualTo(target.getId());
        assertThat(member.getMartId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("없는 회원과 관리자 및 비로그인 요청은 배송지를 삭제할 수 없다")
    void enforcesMemberAuthentication() throws Exception {
        // given
        String missing = tokens.createMemberTokens(Long.MAX_VALUE).accessToken();
        String admin = tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();

        // when
        MockHttpServletResponse missingMember = deleteAddress(target.getId(), missing);
        MockHttpServletResponse administrator = deleteAddress(target.getId(), admin);
        MockHttpServletResponse anonymous = deleteAddress(target.getId(), null);

        // then
        assertError(missingMember, 404, "MEMBER-001");
        assertError(administrator, 403, "AUTH-004");
        assertError(anonymous, 401, "AUTH-001");
        assertThat(addresses.existsById(target.getId())).isTrue();
    }

    private MemberAddress address(Member owner, String code) {
        return addresses.saveAndFlush(MemberAddress.builder().member(owner).address("테스트 주소")
                .detailAddress("101호").legalDongCode(code).build());
    }

    private MockHttpServletResponse deleteAddress(Long addressId, String access) throws Exception {
        var request = delete("/api/members/me/addresses/{id}", addressId);
        if (access != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo(code);
    }
}
