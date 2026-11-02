package com.recipick.domain.member.facade;

import com.recipick.domain.member.dto.request.MembershipSubscribeRequest;
import com.recipick.domain.member.dto.response.MembershipSubscribeResponse;
import com.recipick.domain.payment.facade.MembershipSubscriptionFacade;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MembershipSubscribeFacade {
    private final MembershipSubscriptionFacade subscriptions;
    private final Clock clock;

    // 접수 커밋 후 PG를 호출하도록 결제 Facade 앞에 바깥 트랜잭션을 만들지 않는다.
    @Transactional(propagation = Propagation.NEVER)
    public MembershipSubscribeResponse subscribe(Long memberId, MembershipSubscribeRequest request,
            String idempotencyKey) {
        var consent = request.toConsent(LocalDateTime.now(clock));
        var result = subscriptions.subscribe(memberId, consent, idempotencyKey);
        return new MembershipSubscribeResponse(result.membershipId(), result.status().name());
    }
}
