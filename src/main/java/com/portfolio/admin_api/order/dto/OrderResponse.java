package com.portfolio.admin_api.order.dto;

import java.time.LocalDateTime;

import com.portfolio.admin_api.order.entity.Order;
import com.portfolio.admin_api.order.application.result.CreateOrderResult;

public record OrderResponse(
        Long orderId,
        LocalDateTime createdAt
) {
    public static OrderResponse from(CreateOrderResult result) {
        return new OrderResponse(result.orderId(), result.createdAt());
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCreatedAt()
        );
    }
}
