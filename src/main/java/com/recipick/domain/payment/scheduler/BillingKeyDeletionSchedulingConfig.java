package com.recipick.domain.payment.scheduler;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class BillingKeyDeletionSchedulingConfig {
    public static final String SCHEDULER = "billingKeyDeletionTaskScheduler";

    @Bean(SCHEDULER)
    ThreadPoolTaskScheduler billingKeyDeletionTaskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(2).threadNamePrefix("billing-key-deletion-").build();
    }
}
