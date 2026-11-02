package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 운영자 재조회 요청 접수 감사의 짧은 TX. 외부 결제사 호출 전에 기록·커밋한다(요청 접수 + 로그 → COMMIT → 외부 호출 → 결과 반영).
// 실패하면 PG를 부르지 않는다. 재조회 진입점과 다른 빈이다 — 같은 빈의 자기 호출은 트랜잭션 프록시를 타지 않는다
@Component
@RequiredArgsConstructor
public class MembershipPaymentAdminAuditProcessor {

    private final MembershipPaymentAttemptRepository attempts;
    private final AdminAuditRecorder adminAuditRecorder;

    @Transactional
    public void recordRecheck(Long attemptId) {
        if (!attempts.existsById(attemptId)) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND);
        }
        adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.MEMBERSHIP_PAYMENT_RECHECK, attemptId, null,
            null));
    }
}
