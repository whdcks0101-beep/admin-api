package com.portfolio.admin_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class OutboxSchedulingConfig {
}
