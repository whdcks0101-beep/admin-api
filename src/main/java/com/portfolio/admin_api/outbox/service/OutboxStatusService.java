package com.portfolio.admin_api.outbox.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.outbox.config.OutboxProperties;
import com.portfolio.admin_api.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxStatusService {

	private final OutboxEventRepository outboxEventRepository;
	private final OutboxProperties outboxProperties;

	@Transactional
	public boolean markPublished(Long outboxId, String claimToken) {
		if (claimToken == null || claimToken.isBlank()) {
			throw new IllegalArgumentException("완료 처리에는 선점 토큰이 필요합니다.");
		}

		int updatedCount = outboxEventRepository.markPublishedIfOwned(outboxId, claimToken);

		return updatedCount == 1;
	}

	@Transactional
	public boolean recordFailure(Long outboxId, String claimToken) {
		if (claimToken == null || claimToken.isBlank()) {
			throw new IllegalArgumentException("실패 처리에는 선점 토큰이 필요합니다.");
		}

		int updatedCount = outboxEventRepository.rescheduleIfOwned(outboxId, claimToken, outboxProperties.getRetryDelaySeconds());

		return updatedCount == 1;
	}

}
