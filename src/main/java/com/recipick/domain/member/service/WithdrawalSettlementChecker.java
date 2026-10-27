package com.recipick.domain.member.service;

import java.util.Set;

/**
 * 탈퇴 정산 연결 계약. 실제 상태 판정·데이터 처리 방식은 각 담당자와 합의해 구현한다.
 * 회원 잠금 이후 같은 쓰기 트랜잭션에서 호출하며 외부 PG 호출과 REQUIRES_NEW는 금지한다.
 *
 * <p>상품 결제는 결과 불명·SYSTEM 환불·수동 확인 대상을 포함한다.
 * 멤버십 결제는 첫 구독·자동갱신·재시도·결과 불명·이용료 환불을 포함한다.
 * 포인트는 미정산 예약·적립과 소멸 잔액 사전 안내 및 탈퇴 시 처리를 포함한다.
 * 자동결제는 탈퇴 후 신규 청구 방지와 결제수단 처리까지 포함한다.
 *
 * <p>조회·정리 실패는 예외로 탈퇴를 거절하고 같은 트랜잭션의 변경도 롤백한다.
 * 외부 결제수단 정리가 필요하면 회원 잠금 트랜잭션 밖에서 별도 절차로 완료해야 한다.
 * 이미 시작된 거래의 후속 처리를 탈퇴 검사로 중단해서는 안 된다.
 */
public interface WithdrawalSettlementChecker {
    enum Scope {
        PRODUCT_PAYMENT,
        MEMBERSHIP_PAYMENT,
        POINTS,
        AUTO_BILLING
    }

    // 연결되지 않은 영역을 자동으로 완료 취급하지 않는다. 전체 연결·검증 후 명시적으로 선언한다.
    default Set<Scope> supportedScopes() {
        return Set.of();
    }

    void requireSettled(Long memberId);
}
