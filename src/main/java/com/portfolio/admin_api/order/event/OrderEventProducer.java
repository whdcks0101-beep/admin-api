package com.portfolio.admin_api.order.event;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

	private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

	public CompletableFuture<SendResult<String, OrderCreatedEvent>> send(OrderCreatedEvent event) {

		return kafkaTemplate.send("order-created", event.orderId().toString(), event);
	}
}
