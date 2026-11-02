package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 운영자 환불 재조회 요청 접수 감사의 짧은 TX. 외부 결제사 호출 전에 기록·커밋한다(요청 접수 + 로그 → COMMIT → 외부 호출 → 결과 반영).
// 실패하면 PG 호출이 없다. 재실행 감사는 후속 환불 행 접수와 같은 TX여야 하므로 여기가 아니라 MembershipRefundRetryAuditProcessor가 한다.
// 진입점(운영 Facade)과 다른 빈이다 — 같은 빈의 자기 호출은 트랜잭션 프록시를 타지 않는다
@Component
@RequiredArgsConstructor
public class MembershipRefundAdminAuditProcessor {

    private final MembershipRefundRepository refunds;
    private final AdminAuditRecorder adminAuditRecorder;

    @Transactional
    public void recordRecheck(Long refundId) {
        requireExists(refundId);
        adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.MEMBERSHIP_REFUND_RECHECK, refundId, null, null));
    }

    private void requireExists(Long refundId) {
        if (!refunds.existsById(refundId)) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_NOT_FOUND);
        }
    }
}
