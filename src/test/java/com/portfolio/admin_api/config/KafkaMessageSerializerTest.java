package com.portfolio.admin_api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;

class KafkaMessageSerializerTest {

	@Test
	@DisplayName("주문 이벤트를 직렬화한 뒤 복원하면 원래 데이터와 같다")
	void serializeOrderEvent() {
		OrderCreatedEvent event = new OrderCreatedEvent(35L, LocalDateTime.of(2026, 10, 20, 22, 54, 52));

		try (KafkaMessageSerializer serializer = new KafkaMessageSerializer();
				JacksonJsonDeserializer<OrderCreatedEvent> deserializer = new JacksonJsonDeserializer<>(
						OrderCreatedEvent.class)) {
			byte[] serialized = serializer.serialize("order-created", event);

			OrderCreatedEvent restored = deserializer.deserialize("order-created", serialized);

			assertThat(restored).isEqualTo(event);
		}
	}

	@Test
	@DisplayName("JSON으로 변환할 수 없는 원본 바이트를 그대로 보존한다")
	void preserveInvalidJsonBytes() {
		byte[] original = "invalid-json-test-001".getBytes(StandardCharsets.UTF_8);

		try (KafkaMessageSerializer serializer = new KafkaMessageSerializer()) {
			byte[] result = serializer.serialize("order-created.DLT", original);

			assertThat(result).containsExactly(original);
		}
	}

	@Test
	@DisplayName("빈 메시지의 원본 바이트도 그대로 보존한다")
	void preserveEmptyBytes() {
		byte[] original = new byte[0];

		try (KafkaMessageSerializer serializer = new KafkaMessageSerializer()) {
			byte[] result = serializer.serialize("order-created.DLT", original);

			assertThat(result).isNotNull().isEmpty();
		}
	}
}
