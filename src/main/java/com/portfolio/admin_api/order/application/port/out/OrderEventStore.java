package com.portfolio.admin_api.order.application.port.out;

import com.portfolio.admin_api.order.application.event.OrderCreatedEvent;

public interface OrderEventStore {

	/**
	 * 주문 생성 이벤트를 현재 주문 트랜잭션에 참여해 저장한다.
	 */
	void save(OrderCreatedEvent event);

}
