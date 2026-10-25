package com.portfolio.admin_api.order.event;

import java.time.LocalDateTime;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;
import com.portfolio.admin_api.order.application.port.out.OrderEventStore;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.outbox.entity.OutboxEvent;
import com.portfolio.admin_api.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OrderOutboxWriter implements OrderEventStore {

	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void save(OrderCreatedEvent event) {
		String payload = objectMapper.writeValueAsString(event);

		LocalDateTime databaseNow = outboxEventRepository.findDatabaseNow();

		OutboxEvent outboxEvent = OutboxEvent.create("ORDER_CREATED", event.orderId(), payload, databaseNow);

		outboxEventRepository.save(outboxEvent);
	}
}
