package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;

    // flush까지 해서 webhook_id UNIQUE 충돌이 이 메서드 안에서 터지게 한다. 커밋 시점에 터지면 Facade가 어느 저장이 실패했는지 모른다.
    // 충돌 판정은 트랜잭션 밖(Facade)에서 한다 — 안에서 잡으면 rollback-only로 남아 커밋이 실패한다
    @Transactional
    public WebhookEvent save(WebhookEvent event) {
        return webhookEventRepository.saveAndFlush(event);
    }
}
