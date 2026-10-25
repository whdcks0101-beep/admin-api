package com.portfolio.admin_api.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

	@Min(1)
	private int claimDurationSeconds = 120;

	@Min(1)
	private int retryDelaySeconds = 30;

	@Min(1)
	private int maxEventsPerRun = 100;

	@Min(1)
	private int recoveryBatchSize = 100;
}
