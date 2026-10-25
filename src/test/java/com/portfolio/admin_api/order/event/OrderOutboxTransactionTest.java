package com.portfolio.admin_api.order.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.portfolio.admin_api.inventory.dto.CreateProductRequest;
import com.portfolio.admin_api.inventory.service.InventoryService;
import com.portfolio.admin_api.order.application.command.CreateOrderCommand;
import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;
import com.portfolio.admin_api.order.repository.OrderRepository;
import com.portfolio.admin_api.order.service.OrderService;
import com.portfolio.admin_api.outbox.entity.OutboxStatus;
import com.portfolio.admin_api.outbox.repository.OutboxEventRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:order-outbox-test;MODE=MySQL",
		"app.outbox.enabled=false" })
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderOutboxTransactionTest {

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OutboxEventRepository outboxRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private OrderEventProducer orderEventProducer;

	@Test
	@DisplayName("주문이 커밋되면 해당 주문의 Outbox도 저장되고 직접 전송하지 않는다")
	void commitsOrderAndOutboxTogether() {
		var product = inventoryService.createProduct(new CreateProductRequest("OUTBOX-COMMIT-001", "커밋 테스트 상품", 0, 10));

		var command = new CreateOrderCommand(List.of(new CreateOrderCommand.Item(product.productId(), 2)));

		var response = orderService.createOrder(command);

		assertThat(orderRepository.existsById(response.orderId())).isTrue();

		var events = outboxRepository.findAll().stream().filter(event -> "ORDER_CREATED".equals(event.getEventType())
				&& response.orderId().equals(event.getAggregateId())).toList();

		assertThat(events).hasSize(1);

		var outbox = events.get(0);

		assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(outbox.getClaimToken()).isNull();

		var payload = objectMapper.readValue(outbox.getPayload(), OrderCreatedEvent.class);

		assertThat(payload.orderId()).isEqualTo(response.orderId());

		verifyNoInteractions(orderEventProducer);
	}

	@Test
	@DisplayName("Outbox 저장까지 실행해도 트랜잭션이 롤백되면 주문과 Outbox가 남지 않는다")
	void rollsBackOrderAndOutboxTogether() {
		var product = inventoryService
				.createProduct(new CreateProductRequest("OUTBOX-ROLLBACK-001", "롤백 테스트 상품", 0, 10));

		var command = new CreateOrderCommand(List.of(new CreateOrderCommand.Item(product.productId(), 2)));

		long outboxCountBefore = outboxRepository.count();

		var transaction = new TransactionTemplate(transactionManager);

		Long orderId = transaction.execute(status -> {
			var response = orderService.createOrder(command);

			// 롤백 전에 Outbox 저장 경로까지 실행됐는지 확인한다.
			assertThat(outboxRepository.count()).isEqualTo(outboxCountBefore + 1);

			status.setRollbackOnly();

			return response.orderId();
		});

		assertThat(orderId).isNotNull();
		assertThat(orderRepository.existsById(orderId)).isFalse();
		assertThat(outboxRepository.count()).isEqualTo(outboxCountBefore);

		verifyNoInteractions(orderEventProducer);
	}
}