package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.config.BillingKeyDeletionProperties;
import com.recipick.domain.payment.entity.enums.BillingKeyDeletionReason;
import com.recipick.domain.payment.repository.BillingKeyDeletionRepository;
import com.recipick.domain.payment.service.BillingKeyDeletionCode;
import com.recipick.domain.payment.service.BillingKeyDeletionExecutionResult;
import com.recipick.domain.payment.service.BillingKeyDeletionPolicy;
import com.recipick.domain.payment.service.BillingKeyDeletionRef;
import com.recipick.domain.payment.service.BillingKeyDeletionTicket;
import com.recipick.infra.portone.PortOneBillingKeyDeletionClient;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation.Kind;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BillingKeyDeletionExecutionFacade {
    private final BillingKeyDeletionRepository deletions;
    private final BillingKeyDeletionProcessor processor;
    private final PortOneBillingKeyDeletionClient client;
    private final BillingKeyDeletionProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.NEVER)
    public BillingKeyDeletionExecutionResult execute(Long deletionId) {
        if (!properties.enabled()) {
            return BillingKeyDeletionExecutionResult.SKIPPED;
        }
        // 역조회는 Processor의 쓰기 TX 밖이다. 여기서는 엔티티가 아니라 ID 투영만 읽는다.
        Optional<BillingKeyDeletionRef> ref = deletions.findRef(deletionId);
        if (ref.isEmpty()) {
            return BillingKeyDeletionExecutionResult.SKIPPED;
        }
        Optional<BillingKeyDeletionTicket> claimed = processor.claim(ref.get());
        if (claimed.isEmpty()) {
            return BillingKeyDeletionExecutionResult.SKIPPED;
        }
        BillingKeyDeletionTicket ticket = claimed.get();
        PortOneBillingKeyDeletionObservation observed = client.lookup(ticket.billingKey(), ticket.storeId());
        if (observed.kind() == Kind.ABSENT) {
            return complete(ticket, observed);
        }
        if (observed.kind() != Kind.ISSUED) {
            return finish(ticket, BillingKeyDeletionCode.fromPortOne(observed.code()));
        }
        processor.recordVerifiedIdentity(ticket);
        if (ticket.observationOnly()) {
            return finish(ticket, BillingKeyDeletionCode.STILL_ISSUED);
        }
        if (!BillingKeyDeletionPolicy.deleteWindowOpen(ticket.requestedAt(), LocalDateTime.now(clock))) {
            return finish(ticket, BillingKeyDeletionCode.WINDOW_EXPIRED);
        }
        if (!processor.prepareDelete(ticket)) {
            return BillingKeyDeletionExecutionResult.PENDING;
        }
        BillingKeyDeletionCode reply = BillingKeyDeletionCode.fromPortOne(client.delete(
            ticket.billingKey(), ticket.storeId(), "RECIPICK_" + ticket.reason().name(),
            ticket.reason() == BillingKeyDeletionReason.WITHDRAWAL ? "CUSTOMER" : "ADMIN"));
        // DELETE timeout·이미 삭제 응답도 GET으로 확인한다. 다른 키로 바꾸거나 새 요청 행을 만들지 않는다.
        PortOneBillingKeyDeletionObservation after = client.lookup(ticket.billingKey(), ticket.storeId());
        if (after.kind() == Kind.ABSENT) {
            return complete(ticket, after);
        }
        BillingKeyDeletionCode code = BillingKeyDeletionCode.fromPortOne(after.code());
        if (BillingKeyDeletionPolicy.requiresReview(reply) && !BillingKeyDeletionPolicy.requiresReview(code)) {
            code = reply;
        } else if (after.kind() == Kind.ISSUED) {
            code = BillingKeyDeletionCode.STILL_ISSUED;
        }
        return finish(ticket, code);
    }

    private BillingKeyDeletionExecutionResult complete(BillingKeyDeletionTicket ticket,
                                                       PortOneBillingKeyDeletionObservation observed) {
        return processor.confirmAbsent(ticket, observed) ? BillingKeyDeletionExecutionResult.COMPLETED
            : BillingKeyDeletionExecutionResult.REVIEW_REQUIRED;
    }

    private BillingKeyDeletionExecutionResult finish(BillingKeyDeletionTicket ticket, BillingKeyDeletionCode code) {
        boolean updated = processor.defer(ticket, code);
        if (!updated) {
            // 만료된 실행자가 현재 결과를 추정하지 않는다. 다음 실행자가 이어받는다.
            return BillingKeyDeletionExecutionResult.PENDING;
        }
        return ticket.observationOnly() || BillingKeyDeletionPolicy.requiresReview(code)
            || !BillingKeyDeletionPolicy.deleteWindowOpen(ticket.requestedAt(), LocalDateTime.now(clock))
            ? BillingKeyDeletionExecutionResult.REVIEW_REQUIRED : BillingKeyDeletionExecutionResult.PENDING;
    }
}
