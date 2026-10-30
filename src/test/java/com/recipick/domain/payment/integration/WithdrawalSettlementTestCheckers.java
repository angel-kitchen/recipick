package com.recipick.domain.payment.integration;

import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

// 1단계에는 PRODUCT_PAYMENT·POINTS Checker만 있어 운영 구성의 탈퇴 API는 MEMBER-008(503)로 끝난다.
// 탈퇴 API를 끝까지 태우는 테스트만 이 설정을 @Import해 MEMBERSHIP_PAYMENT(4단계)·AUTO_BILLING(6단계) 자리를 아무것도 하지 않는
// 스텁으로 채운다. 운영 순서(100 → 200 → 300 → 400)를 그대로 두고, 200번 스텁에 테스트가 탈퇴 트랜잭션을 멈출 수 있는 손잡이를 단다
@TestConfiguration
class WithdrawalSettlementTestCheckers {

    @Bean
    SettlementGate settlementGate() {
        return new SettlementGate();
    }

    @Bean
    @Order(200)
    WithdrawalSettlementChecker membershipPaymentStubChecker(SettlementGate gate) {
        return new StubChecker(WithdrawalSettlementChecker.Scope.MEMBERSHIP_PAYMENT, gate);
    }

    @Bean
    @Order(300)
    WithdrawalSettlementChecker autoBillingStubChecker() {
        return new StubChecker(WithdrawalSettlementChecker.Scope.AUTO_BILLING, null);
    }

    // 탈퇴 트랜잭션이 스냅숏을 만든 뒤(진행 중 주문 일반 조회) · 결제 검사를 마친 뒤 · 포인트 검사 전에 실행되는 자리.
    // 테스트가 여기서 다른 트랜잭션의 커밋을 기다리게 해 스냅숏과 잠금 읽기의 차이를 재현한다. 기본은 아무것도 하지 않는다
    static class SettlementGate {

        private final AtomicReference<Runnable> hook = new AtomicReference<>(() -> { });

        void set(Runnable runnable) {
            hook.set(runnable);
        }

        void clear() {
            hook.set(() -> { });
        }

        void pass() {
            hook.get().run();
        }
    }

    private record StubChecker(WithdrawalSettlementChecker.Scope scope, SettlementGate gate)
        implements WithdrawalSettlementChecker {

        @Override
        public Set<Scope> supportedScopes() {
            return Set.of(scope);
        }

        @Override
        public void requireSettled(Long memberId, Long acknowledgedPointBalance) {
            if (gate != null) {
                gate.pass();
            }
        }
    }
}
