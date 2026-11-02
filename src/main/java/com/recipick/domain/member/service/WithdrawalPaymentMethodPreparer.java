package com.recipick.domain.member.service;

// 회원 재인증·기본 제한 확인 후, 회원 행이 잠긴 쓰기 TX에서 호출한다.
// 구현은 외부 PG를 호출하지 않고 삭제 요청만 저장한다. 결과를 예외로 바꾸는 곳은 TX 밖 Facade다.
public interface WithdrawalPaymentMethodPreparer {
    boolean isEnabled();
    WithdrawalPaymentMethodPreparation prepare(Long memberId);
}
