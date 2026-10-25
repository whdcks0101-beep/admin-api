package com.portfolio.admin_api.order.service;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.common.dto.PageResponse;
import com.portfolio.admin_api.inventory.entity.Inventory;
import com.portfolio.admin_api.inventory.repository.InventoryRepository;
import com.portfolio.admin_api.order.application.command.CreateOrderCommand;
import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;
import com.portfolio.admin_api.order.application.port.out.OrderEventStore;
import com.portfolio.admin_api.order.application.result.CreateOrderResult;
import com.portfolio.admin_api.order.dto.OrderDetailResponse;
import com.portfolio.admin_api.order.dto.OrderItemResponse;
import com.portfolio.admin_api.order.dto.OrderResponse;
import com.portfolio.admin_api.order.dto.OrderSearchRequest;
import com.portfolio.admin_api.order.entity.Order;
import com.portfolio.admin_api.order.entity.OrderItem;
import com.portfolio.admin_api.order.repository.OrderItemQueryRepository;
import com.portfolio.admin_api.order.repository.OrderItemRepository;
import com.portfolio.admin_api.order.repository.OrderQueryRepository;
import com.portfolio.admin_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final InventoryRepository inventoryRepository;
	private final OrderItemQueryRepository orderItemQueryRepository;
	private final OrderQueryRepository orderQueryRepository;
	private final OrderEventStore orderEventStore;

	@Transactional
	public CreateOrderResult createOrder(CreateOrderCommand command) {

		// 1. 같은 상품이 여러 줄로 들어왔는지 확인
		Set<Long> productIds = new HashSet<>();

		for (CreateOrderCommand.Item item : command.items()) {
			if (!productIds.add(item.productId())) {
				throw new IllegalArgumentException("같은 상품은 한 번만 입력해 주세요.");
			}
		}

		// 2. 주문 저장
		Order order = orderRepository.save(Order.create());

		// 3. 상품별 재고 차감과 주문 항목 저장
		for (CreateOrderCommand.Item item : command.items()) {
			Inventory inventory = inventoryRepository.findByProduct_Id(item.productId())
					.orElseThrow(() -> new NoSuchElementException("상품 재고를 찾을 수 없습니다. 상품 번호=" + item.productId()));

			inventory.decrease(item.quantity());

			OrderItem orderItem = new OrderItem(order, inventory.getProduct(), item.quantity());

			orderItemRepository.save(orderItem);
		}

		// 4. 주문 생성 이벤트를 현재 트랜잭션에 함께 저장
		orderEventStore.save(new OrderCreatedEvent(order.getId(), order.getCreatedAt()));

		// 5. 주문 처리 결과 반환
		return new CreateOrderResult(order.getId(), order.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public OrderDetailResponse getOrder(Long orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new NoSuchElementException("주문을 찾을 수 없습니다. 주문 번호=" + orderId));

		List<OrderItemResponse> items = orderItemQueryRepository.findAllWithProductByOrderId(orderId).stream()
				.map(OrderItemResponse::from).toList();

		return OrderDetailResponse.from(order, items);
	}

	@Transactional(readOnly = true)
	public PageResponse<OrderResponse> getOrders(OrderSearchRequest request) {
		PageRequest pageable = PageRequest.of(request.getPage(), request.getSize());

		Page<Order> orders = orderQueryRepository.search(request, pageable);

		Page<OrderResponse> responses = orders.map(OrderResponse::from);

		return PageResponse.from(responses);
	}

}
