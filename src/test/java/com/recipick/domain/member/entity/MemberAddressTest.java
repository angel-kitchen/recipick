package com.recipick.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberAddressTest {

    private MemberAddress address(Member member) {
        return MemberAddress.builder().member(member).address("도로명 주소").detailAddress("101호")
                .legalDongCode("9999999999").build();
    }

    @Test
    @DisplayName("지역 조회 없이 서비스 밖 법정동 코드의 배송지를 생성할 수 있다")
    void acceptsOutsideServiceArea() {
        // given
        Member member = Member.builder().build();
        // when
        MemberAddress address = address(member);
        member.selectAddress(address, null);
        // then
        assertThat(address.getLegalDongCode()).isEqualTo("9999999999");
        assertThat(address.getLabel()).isNull();
        assertThat(member.getSelectedAddress()).isSameAs(address);
        assertThat(member.getMartId()).isNull();
    }

    @Test
    @DisplayName("배송지를 선택하면 이용 마트도 함께 변경한다")
    void selectsAddressAndMart() {
        // given
        Member member = Member.builder().build();
        MemberAddress address = address(member);
        // when
        member.selectAddress(address, 1L);
        // then
        assertThat(member.getSelectedAddress()).isSameAs(address);
        assertThat(member.getMartId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 회원의 배송지는 현재 배송지로 선택할 수 없다")
    void rejectsOtherOwnersAddress() {
        // given
        Member owner = Member.builder().build();
        Member other = Member.builder().build();
        MemberAddress address = address(owner);
        // when
        Throwable thrown = catchThrowable(() -> other.selectAddress(address, 1L));
        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(other.getSelectedAddress()).isNull();
        assertThat(other.getMartId()).isNull();
    }

    @Test
    @DisplayName("주소 변경 검증에 실패하면 기존 주소를 유지한다")
    void keepsOriginalAddressOnInvalidChange() {
        // given
        MemberAddress address = address(Member.builder().build());
        // when
        Throwable thrown = catchThrowable(() -> address.changeAddress("변경할 주소", " ", "9999999999"));
        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(address.getAddress()).isEqualTo("도로명 주소");
        assertThat(address.getDetailAddress()).isEqualTo("101호");
    }

    @Test
    @DisplayName("주소와 상세 주소와 법정동 코드를 함께 변경하고 명칭은 지울 수 있다")
    void updatesAddressTogether() {
        // given
        MemberAddress address = address(Member.builder().build());
        address.rename("집");
        // when
        address.changeAddress("다른 주소", "202호", "1111111111");
        address.rename(null);
        // then
        assertThat(address.getAddress()).isEqualTo("다른 주소");
        assertThat(address.getDetailAddress()).isEqualTo("202호");
        assertThat(address.getLegalDongCode()).isEqualTo("1111111111");
        assertThat(address.getLabel()).isNull();
    }

    @Test
    @DisplayName("위치 동의는 최초 시각만 기록한다")
    void preservesFirstLocationConsent() {
        // given
        Member member = Member.builder().build();
        LocalDateTime first = LocalDateTime.of(2026, 10, 20, 10, 0);
        // when
        member.agreeToLocation(first);
        member.agreeToLocation(first.plusDays(1));
        // then
        assertThat(member.getLocationAgreedAt()).isEqualTo(first);
    }
}
