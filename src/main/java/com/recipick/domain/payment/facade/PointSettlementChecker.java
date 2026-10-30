package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.payment.service.PointCommandService;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 탈퇴 정산의 POINTS 영역. 회원 탈퇴 트랜잭션(회원 잠금 뒤)이 Scope 순서대로 부른다.
// 포인트는 소멸이라는 변경이 있어 네 Scope 중 마지막(400)에 둔다 — 앞 Scope에서 막히면 포인트를 건드리기 전에 끝난다.
// 판정·소멸 규칙은 PointCommandService가 갖고, 여기서는 계약(Scope·호출)만 잇는다
@Component
@Order(400)
@RequiredArgsConstructor
public class PointSettlementChecker implements WithdrawalSettlementChecker {

    private final PointCommandService pointCommandService;

    @Override
    public Set<Scope> supportedScopes() {
        return Set.of(Scope.POINTS);
    }

    // 확인 잔액은 회원 요청에서 필수(0 이상)라 null이면 호출 순서 버그다
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireSettled(Long memberId, Long acknowledgedPointBalance) {
        Objects.requireNonNull(acknowledgedPointBalance, "확인한 포인트 잔액은 필수입니다.");
        pointCommandService.forfeitForWithdrawal(memberId, acknowledgedPointBalance);
    }
}
