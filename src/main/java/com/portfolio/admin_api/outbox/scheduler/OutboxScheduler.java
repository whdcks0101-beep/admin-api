package com.portfolio.admin_api.outbox.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.portfolio.admin_api.outbox.service.OutboxRecoveryService;
import com.portfolio.admin_api.outbox.service.OutboxRelay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = false)
public class OutboxScheduler {

	private final OutboxRecoveryService outboxRecoveryService;
	private final OutboxRelay outboxRelay;

	@Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:5000}", initialDelayString = "${app.outbox.initial-delay-ms:10000}")
	public void publishPending() {
		try {
			int recoveredCount = outboxRecoveryService.recoverExpiredClaims();

			if (recoveredCount > 0) {
				log.info("Outbox 만료 작업 복구 완료: count={}", recoveredCount);
			}

			outboxRelay.publishPending();
		} catch (RuntimeException e) {
			log.error("Outbox 주기 실행 실패", e);
		}
	}
}