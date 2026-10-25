package com.portfolio.admin_api.order.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderEventConsumer {

	@KafkaListener(topics = "order-created")
	public void consume(ConsumerRecord<String, OrderCreatedEvent> record) {
		OrderCreatedEvent event = record.value();

        log.info(
                "주문 이벤트 Kafka 수신: topic={}, partition={}, offset={}, key={}, orderId={}, createdAt={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                event.orderId(),
                event.createdAt()
        );
	}

}
