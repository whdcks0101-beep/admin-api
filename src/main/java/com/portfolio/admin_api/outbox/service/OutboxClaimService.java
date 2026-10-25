package com.portfolio.admin_api.outbox.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.outbox.config.OutboxProperties;
import com.portfolio.admin_api.outbox.entity.OutboxEvent;
import com.portfolio.admin_api.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

	private final OutboxEventRepository outboxEventRepository;
	private final OutboxProperties outboxProperties;

	@Transactional
	public Optional<OutboxEvent> claimNext() {
		List<OutboxEvent> events = outboxEventRepository.findPendingForClaim(1);

		if (events.isEmpty()) {
			return Optional.empty();
		}

		OutboxEvent event = events.get(0);

		String claimToken = UUID.randomUUID().toString();
		LocalDateTime databaseNow = outboxEventRepository.findDatabaseNow();		
		
		LocalDateTime lockedUntil = databaseNow.plusSeconds(outboxProperties.getClaimDurationSeconds());

		event.claim(claimToken, lockedUntil);

		return Optional.of(event);
	}
}
