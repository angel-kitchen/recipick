package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.infra.portone.dto.PortOneBillingKeyLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 빌링키 폐기 반영의 짧은 TX. 잠금 순서 MEMBER(정산 잠금 — 탈퇴 회원 포함) → 키 PK. 키 상태 변경은 모두 회원 잠금 아래라
// 같은 회원의 카드 등록·교체와 겹치지 않는다. 소유가 다르면 예외. 폐기 근거가 명확할 때만 이 키 한 행을 REVOKED로 바꾼다 —
// 다른 키·결제 시도 상태는 건드리지 않는다(UNKNOWN 시도는 같은 결제 ID 재조회를 계속한다)
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingKeyRevocationProcessor {

    private final MemberService memberService;
    private final BillingKeyRepository billingKeys;

    @Transactional
    public boolean revokeIfConfirmed(Long memberId, Long billingKeyId, PortOneBillingKeyLookup lookup) {
        memberService.lockMemberForSettlement(memberId);
        BillingKey key = billingKeys.findByIdForUpdate(billingKeyId)
            .orElseThrow(() -> new IllegalStateException("빌링키가 사라짐: billingKeyId=" + billingKeyId));
        if (!key.getMemberId().equals(memberId)) {
            throw new IllegalStateException("빌링키 소유 회원이 다름: billingKeyId=" + billingKeyId);
        }
        if (!lookup.confirmsRevocation()) {
            return false;
        }
        boolean revoked = key.revoke();
        if (revoked) {
            log.warn("PortOne이 키 삭제·없음을 확인 → 빌링키 REVOKED: memberId={}, billingKeyId={}, code={}", memberId,
                billingKeyId, lookup.code());
        }
        return revoked;
    }
}
