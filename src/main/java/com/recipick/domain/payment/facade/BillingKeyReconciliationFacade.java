package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.service.BillingKeyRecheckRef;
import com.recipick.infra.portone.PortOneBillingClient;
import com.recipick.infra.portone.dto.PortOneBillingKeyLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 빌링키 상태 재확인. 키 GET은 트랜잭션 밖에서 하고 반영은 BillingKeyRevocationProcessor의 짧은 TX가 한다.
// verifyUnusable: 청구 POST가 "사용할 수 없는 키"로 거절된 뒤 그 키만. recheck: 결과 불명 창 안의 404 시도에 묶인 키(복구 Sweep).
// 키 GET이 명확히 삭제·없음이라고 답할 때만 REVOKED가 된다. 조회 장애·권한 오류·다른 404는 아무것도 바꾸지 않는다.
// 키 원문은 키 행에서 읽어 PG 호출에만 쓰고 로그에 남기지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingKeyReconciliationFacade {

    private final BillingKeyRepository billingKeys;
    private final PortOneBillingClient portOneBillingClient;
    private final BillingKeyRevocationProcessor revocationProcessor;

    @Transactional(propagation = Propagation.NEVER)
    public boolean verifyUnusable(Long memberId, Long billingKeyId) {
        return check(memberId, billingKeyId);
    }

    @Transactional(propagation = Propagation.NEVER)
    public boolean recheck(BillingKeyRecheckRef ref) {
        return check(ref.memberId(), ref.billingKeyId());
    }

    private boolean check(Long memberId, Long billingKeyId) {
        String portoneBillingKey = billingKeys.findById(billingKeyId).map(BillingKey::getPortoneBillingKey)
            .orElseThrow(() -> new IllegalStateException("빌링키가 없음: billingKeyId=" + billingKeyId));
        PortOneBillingKeyLookup lookup = portOneBillingClient.lookupForReconciliation(portoneBillingKey);
        if (lookup.kind() == PortOneBillingKeyLookup.Kind.UNAVAILABLE) {
            log.warn("빌링키 상태를 확인하지 못해 변경하지 않음: billingKeyId={}, code={}", billingKeyId, lookup.code());
            return false;
        }
        return revocationProcessor.revokeIfConfirmed(memberId, billingKeyId, lookup);
    }
}
