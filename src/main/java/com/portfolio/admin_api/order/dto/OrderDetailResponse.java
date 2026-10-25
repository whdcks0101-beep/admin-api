package com.portfolio.admin_api.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.portfolio.admin_api.order.entity.Order;

public record OrderDetailResponse(
        Long orderId,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {

    public static OrderDetailResponse from(
            Order order,
            List<OrderItemResponse> items
    ) {
        return new OrderDetailResponse(
                order.getId(),
                order.getCreatedAt(),
                List.copyOf(items)
        );
    }
}
