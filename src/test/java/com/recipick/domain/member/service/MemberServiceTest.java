package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    private Member member(Long martId) {
        return Member.builder()
                .email("member@example.com")
                .name("홍길동")
                .phone("01012345678")
                .passwordHash("hash")
                .martId(martId)
                .build();
    }

    @Test
    @DisplayName("이용 마트가 있으면 마트 ID를 돌려준다")
    void getMartId() {
        // given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(7L)));

        // when
        Optional<Long> martId = memberService.getMartId(MEMBER_ID);

        // then
        assertThat(martId).contains(7L);
    }

    @Test
    @DisplayName("이용 마트가 없으면 empty를 돌려준다")
    void getMartIdWithoutMart() {
        // given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(null)));

        // when
        Optional<Long> martId = memberService.getMartId(MEMBER_ID);

        // then
        assertThat(martId).isEmpty();
    }

    @Test
    @DisplayName("회원이 없으면 MEMBER_NOT_FOUND")
    void getMartIdMemberNotFound() {
        // given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> memberService.getMartId(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }
}
