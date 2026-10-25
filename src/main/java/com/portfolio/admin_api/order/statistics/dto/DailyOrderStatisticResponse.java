package com.portfolio.admin_api.order.statistics.dto;

import java.time.LocalDate;

public record DailyOrderStatisticResponse(
        LocalDate orderDate,
        Long orderCount
) {
}