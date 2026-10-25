package com.portfolio.admin_api.order.event;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;
import com.portfolio.admin_api.outbox.entity.OutboxEvent;
import com.portfolio.admin_api.outbox.sender.OutboxEventSender;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OrderOutboxSender implements OutboxEventSender {

	private final ObjectMapper objectMapper;
	private final OrderEventProducer orderEventProducer;

	@Override
	public void send(OutboxEvent outboxEvent) {
		if (!supportedEventType().equals(outboxEvent.getEventType())) {
			throw new IllegalArgumentException("지원하지 않는 이벤트 유형입니다: " + outboxEvent.getEventType());
		}

		OrderCreatedEvent event = objectMapper.readValue(outboxEvent.getPayload(), OrderCreatedEvent.class);

		try {
			orderEventProducer.send(event).get(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("주문 이벤트 전송 대기가 중단됐습니다.", e);
		} catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException("주문 이벤트 전송을 확인하지 못했습니다.", e);
		}
	}

	@Override
	public String supportedEventType() {
		return "ORDER_CREATED";
	}

}
