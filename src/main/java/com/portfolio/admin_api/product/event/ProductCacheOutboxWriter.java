package com.portfolio.admin_api.product.event;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.outbox.entity.OutboxEvent;
import com.portfolio.admin_api.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ProductCacheOutboxWriter {

	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.MANDATORY)
	public void save(ProductCacheInvalidationEvent event) {
		String payload = objectMapper.writeValueAsString(event);

		LocalDateTime databaseNow = outboxEventRepository.findDatabaseNow();

		OutboxEvent outboxEvent = OutboxEvent.create("PRODUCT_CACHE_INVALIDATION_REQUESTED", event.productId(), payload,
				databaseNow);

		outboxEventRepository.save(outboxEvent);
	}
}