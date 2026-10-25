package com.portfolio.admin_api.config;

import java.util.Map;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;

public class KafkaMessageSerializer extends DelegatingByTypeSerializer {

	public KafkaMessageSerializer() {
		super(Map.of(OrderCreatedEvent.class, new JacksonJsonSerializer<>(), byte[].class, new ByteArraySerializer()));
	}

}
