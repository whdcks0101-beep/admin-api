package com.portfolio.admin_api.order.application.result;

import java.time.LocalDateTime;

public record CreateOrderResult(
        Long orderId,
        LocalDateTime createdAt
) {
}