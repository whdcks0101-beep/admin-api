package com.portfolio.admin_api.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockChangeRequest(
        @NotNull(message = "변경 수량은 필수입니다.")
        @Min(value = 1, message = "변경 수량은 1 이상이어야 합니다.")
        Integer quantity
) {
}
