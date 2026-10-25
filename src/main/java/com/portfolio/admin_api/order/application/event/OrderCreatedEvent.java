package com.portfolio.admin_api.order.application.event;

import java.time.LocalDateTime;

public record OrderCreatedEvent(
        Long orderId,
        LocalDateTime createdAt
) {
}