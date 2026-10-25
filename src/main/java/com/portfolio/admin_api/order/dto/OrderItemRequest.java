package com.portfolio.admin_api.order.dto;

import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
		
        @Schema(
                description = "주문할 상품 번호. 실제 등록된 상품의 ID를 사용합니다.",
                example = "1"
        )
        @NotNull(message = "상품 번호는 필수입니다.")
        @Min(value = 1, message = "상품 번호는 1 이상이어야 합니다.")
        Long productId,

        @Schema(
                description = "주문 수량",
                example = "2"
        )
        @NotNull(message = "주문 수량은 필수입니다.")
        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다.")
        Integer quantity
) {
}
