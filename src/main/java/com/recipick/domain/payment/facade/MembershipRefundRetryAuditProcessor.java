package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.service.MembershipRefundAcceptResult;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 운영자 SYSTEM 환불 재실행의 접수 + 감사 TX. 새 후속 환불 행을 만드는 변경과 그 감사 기록을 같은 트랜잭션으로 커밋한다 —
// 감사 저장이 실패하면 후속 행도 롤백되고 PG 호출은 없다. 기존 후속 행을 그대로 돌려주는 멱등 재요청과 거절은 새 실행이 아니므로
// 감사를 남기지 않는다(요청 자체를 감사하는 재조회와 다르다). 이 TX의 첫 DB 작업은 접수 TX의 회원 잠금이다 — 앞에 조회를 두지 않는다.
// 진입점(운영 Facade)과 다른 빈이다 — 같은 빈의 자기 호출은 트랜잭션 프록시를 타지 않는다
@Component
@RequiredArgsConstructor
public class MembershipRefundRetryAuditProcessor {

    private final MembershipRefundRequestProcessor requestProcessor;
    private final AdminAuditRecorder adminAuditRecorder;

    @Transactional
    public MembershipRefundAcceptResult acceptAndRecord(Long memberId, Long failedRefundId) {
        MembershipRefundAcceptResult accepted = requestProcessor.acceptSystemRetry(memberId, failedRefundId);
        if (accepted.requiresExecution()) {
            adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.MEMBERSHIP_REFUND_RETRY, failedRefundId,
                null, null));
        }
        return accepted;
    }
}
