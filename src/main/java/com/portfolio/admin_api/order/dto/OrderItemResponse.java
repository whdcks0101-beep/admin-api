package com.portfolio.admin_api.order.dto;

import com.portfolio.admin_api.order.entity.OrderItem;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주문 상세 조회")
public record OrderItemResponse(
        Long productId,
        String sku,
        String productName,
        int quantity
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getQuantity()
        );
    }
}
