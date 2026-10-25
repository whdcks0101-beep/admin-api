package com.portfolio.admin_api.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank(message = "상품 코드는 필수입니다.")
        @Size(max = 40, message = "상품 코드는 40자 이하여야 합니다.")
        String sku,

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String name,

        @Min(value = 0, message = "안전 재고는 0 이상이어야 합니다.")
        int safetyStock,

        @NotNull(message = "초기 재고는 필수입니다.")
        @Min(value = 0, message = "초기 재고는 0 이상이어야 합니다.")
        Integer initialQuantity
) {
}
