package com.portfolio.admin_api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;

@EmbeddedKafka(partitions = 1, topics = { "retry-test", "retry-test.DLT", "invalid-json-test",
		"invalid-json-test.DLT" })
class KafkaRetryDltTest {

	private static final String TOPIC = "retry-test";
	private static final String DLT = "retry-test.DLT";
	private static final String INVALID_TOPIC = "invalid-json-test";
	private static final String INVALID_DLT = "invalid-json-test.DLT";

	@Test
	@DisplayName("소비자 처리가 세 번 실패하면 이벤트를 DLT에 보관한다")
	void retryThenPublishToDlt(EmbeddedKafkaBroker broker) throws Exception {
		AtomicInteger attempts = new AtomicInteger();

		// 1. 테스트 Kafka로 보내는 생산자
		Map<String, Object> producerProperties = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
				broker.getBrokersAsString(), ProducerConfig.ACKS_CONFIG, "all");

		var producerFactory = new DefaultKafkaProducerFactory<String, Object>(producerProperties,
				new StringSerializer(), new KafkaMessageSerializer());

		var kafkaTemplate = new KafkaTemplate<>(producerFactory);

		// 2. 원본 토픽을 읽는 소비자
		var consumerFactory = new DefaultKafkaConsumerFactory<String, OrderCreatedEvent>(
				consumerProperties(broker, "retry-test-group"), new StringDeserializer(),
				new JacksonJsonDeserializer<>(OrderCreatedEvent.class, false));

		var containerProperties = new ContainerProperties(TOPIC);

		var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

		// 실제 프로젝트의 재시도·DLT 설정을 재사용한다.
		container.setCommonErrorHandler(new KafkaConsumerConfig().kafkaErrorHandler(kafkaTemplate));

		// 이 테스트 안에서만 항상 실패하는 소비자를 만든다.
		container.setupMessageListener((MessageListener<String, OrderCreatedEvent>) record -> {
			attempts.incrementAndGet();
			throw new IllegalStateException("테스트용 처리 실패");
		});

		// 3. DLT에 저장된 결과를 확인하는 별도 소비자
		try (var dltConsumer = new KafkaConsumer<String, OrderCreatedEvent>(
				consumerProperties(broker, "dlt-reader-group"), new StringDeserializer(),
				new JacksonJsonDeserializer<>(OrderCreatedEvent.class, false))) {
			broker.consumeFromAnEmbeddedTopic(dltConsumer, DLT);

			container.start();
			ContainerTestUtils.waitForAssignment(container, 1);

			OrderCreatedEvent event = new OrderCreatedEvent(100L, LocalDateTime.of(2026, 10, 20, 23, 10));

			// 전송 완료까지 최대 10초 기다린다.
			kafkaTemplate.send(TOPIC, "100", event).get(10, TimeUnit.SECONDS);

			// DLT에 메시지가 도착할 때까지 최대 20초 기다린다.
			var received = KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(20));

			assertThat(attempts.get()).isEqualTo(3);
			assertThat(received.topic()).isEqualTo(DLT);
			assertThat(received.key()).isEqualTo("100");
			assertThat(received.value()).isEqualTo(event);
		} finally {
			container.stop();
			kafkaTemplate.destroy();
			producerFactory.destroy();
		}
	}

	@Test
	@DisplayName("잘못된 JSON은 업무 소비자를 실행하지 않고 원본 바이트 그대로 DLT에 보관한다")
	void invalidJsonIsPreservedInDlt(EmbeddedKafkaBroker broker) throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		String groupId = "invalid-json-test-group";
		byte[] original = "invalid-json-test-001".getBytes(StandardCharsets.UTF_8);

		Map<String, Object> producerProperties = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
				broker.getBrokersAsString(), ProducerConfig.ACKS_CONFIG, "all");

		// 운영 코드의 직렬화기를 사용해 원본 byte[] 전송 경로도 검증한다.
		var producerFactory = new DefaultKafkaProducerFactory<String, Object>(producerProperties,
				new StringSerializer(), new KafkaMessageSerializer());
		var kafkaTemplate = new KafkaTemplate<>(producerFactory);

		var deserializer = new ErrorHandlingDeserializer<OrderCreatedEvent>(
				new JacksonJsonDeserializer<>(OrderCreatedEvent.class, false));
		var consumerFactory = new DefaultKafkaConsumerFactory<String, OrderCreatedEvent>(
				consumerProperties(broker, groupId), new StringDeserializer(), deserializer);
		var container = new KafkaMessageListenerContainer<>(consumerFactory, new ContainerProperties(INVALID_TOPIC));

		container.setCommonErrorHandler(new KafkaConsumerConfig().kafkaErrorHandler(kafkaTemplate));
		container.setupMessageListener((MessageListener<String, OrderCreatedEvent>) record -> {
			attempts.incrementAndGet();
		});

		// DLT의 잘못된 JSON을 다시 JSON으로 해석하지 않고 원본 바이트로 읽는다.
		try (var dltConsumer = new KafkaConsumer<String, byte[]>(
				consumerProperties(broker, "invalid-json-dlt-reader-group"), new StringDeserializer(),
				new ByteArrayDeserializer())) {
			broker.consumeFromAnEmbeddedTopic(dltConsumer, INVALID_DLT);
			container.start();
			ContainerTestUtils.waitForAssignment(container, 1);

			var sent = kafkaTemplate.send(INVALID_TOPIC, "invalid-json-key", original).get(10, TimeUnit.SECONDS);
			var received = KafkaTestUtils.getSingleRecord(dltConsumer, INVALID_DLT, Duration.ofSeconds(20));

			assertThat(attempts.get()).isZero();
			assertThat(received.topic()).isEqualTo(INVALID_DLT);
			assertThat(received.key()).isEqualTo("invalid-json-key");
			assertThat(received.value()).containsExactly(original);

			// DLT 헤더로 실패한 원본 위치와 소비자 그룹까지 추적할 수 있다.
			assertThat(received.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)).isNotNull();
			assertThat(new String(received.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
					StandardCharsets.UTF_8)).isEqualTo(INVALID_TOPIC);
			assertThat(received.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)).isNotNull();
			assertThat(new String(received.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP).value(),
					StandardCharsets.UTF_8)).isEqualTo(groupId);
			assertThat(received.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();
			assertThat(java.nio.ByteBuffer.wrap(received.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET).value())
					.getLong()).isEqualTo(sent.getRecordMetadata().offset());
		} finally {
			container.stop();
			kafkaTemplate.destroy();
			producerFactory.destroy();
		}
	}

	private Map<String, Object> consumerProperties(EmbeddedKafkaBroker broker, String groupId) {

		Map<String, Object> properties = new HashMap<>();

		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

		return properties;
	}
}
