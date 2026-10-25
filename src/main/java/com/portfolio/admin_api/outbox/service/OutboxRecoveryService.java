package com.portfolio.admin_api.outbox.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.outbox.config.OutboxProperties;
import com.portfolio.admin_api.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxRecoveryService {

	private final OutboxEventRepository outboxEventRepository;
	private final OutboxProperties outboxProperties;

	@Transactional
	public int recoverExpiredClaims() {
		return outboxEventRepository.recoverExpiredClaims(outboxProperties.getRecoveryBatchSize());
	}

}
