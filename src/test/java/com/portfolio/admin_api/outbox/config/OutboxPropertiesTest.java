package com.portfolio.admin_api.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OutboxPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesTestConfig.class);

	@Test
	@DisplayName("Outbox 설정을 생략하면 기본값을 사용한다")
	void usesDefaultsWhenPropertiesAreMissing() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();

			OutboxProperties properties = context.getBean(OutboxProperties.class);

			assertThat(properties.getClaimDurationSeconds()).isEqualTo(120);
			assertThat(properties.getRetryDelaySeconds()).isEqualTo(30);
			assertThat(properties.getMaxEventsPerRun()).isEqualTo(100);
			assertThat(properties.getRecoveryBatchSize()).isEqualTo(100);
		});
	}

	@Test
	@DisplayName("Outbox 설정을 지정하면 기본값을 덮어쓴다")
	void overridesDefaultsWithConfiguredValues() {
		contextRunner.withPropertyValues("app.outbox.claim-duration-seconds=180", "app.outbox.retry-delay-seconds=45",
				"app.outbox.max-events-per-run=20", "app.outbox.recovery-batch-size=10").run(context -> {
					assertThat(context).hasNotFailed();

					OutboxProperties properties = context.getBean(OutboxProperties.class);

					assertThat(properties.getClaimDurationSeconds()).isEqualTo(180);
					assertThat(properties.getRetryDelaySeconds()).isEqualTo(45);
					assertThat(properties.getMaxEventsPerRun()).isEqualTo(20);
					assertThat(properties.getRecoveryBatchSize()).isEqualTo(10);
				});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(OutboxProperties.class)
	static class PropertiesTestConfig {
	}

	@Test
	@DisplayName("재시도 대기 시간이 0이면 설정 검증에 실패한다")
	void rejectsZeroRetryDelay() {
		contextRunner.withPropertyValues("app.outbox.retry-delay-seconds=0").run(context -> {
			assertThat(context).hasFailed();

			assertThat(context.getStartupFailure()).hasStackTraceContaining("retryDelaySeconds")
					.hasStackTraceContaining("Min");
		});
	}

}
