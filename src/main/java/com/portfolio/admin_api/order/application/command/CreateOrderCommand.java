package com.portfolio.admin_api.order.application.command;

import java.util.List;
import java.util.Objects;

public record CreateOrderCommand(List<Item> items) {

	public CreateOrderCommand {
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("주문할 상품을 하나 이상 입력해 주세요.");
		}

		if (items.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("주문 항목은 null일 수 없습니다.");
		}

		items = List.copyOf(items);
	}

	public record Item(Long productId, Integer quantity) {

		public Item {
			if (productId == null || productId <= 0) {
				throw new IllegalArgumentException("상품 번호는 1 이상이어야 합니다.");
			}

			if (quantity == null || quantity <= 0) {
				throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다.");
			}
		}
	}
}