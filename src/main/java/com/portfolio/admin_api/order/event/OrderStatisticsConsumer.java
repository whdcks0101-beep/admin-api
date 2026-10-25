package com.portfolio.admin_api.order.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;
import com.portfolio.admin_api.order.statistics.service.OrderStatisticService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatisticsConsumer {
	
	private final OrderStatisticService orderStatisticService;

	@KafkaListener(topics = "order-created", groupId = "order-statistics-group")
	public void consume(ConsumerRecord<String, OrderCreatedEvent> record) {
		OrderCreatedEvent event = record.value();

		orderStatisticService.recordOrder(event.orderId(), event.createdAt());
		
        log.info(
                "주문 통계용 이벤트 수신: topic={}, partition={}, offset={}, orderId={}",
                record.topic(),
                record.partition(),
                record.offset(),
                event.orderId()
        );
        
	}

}
