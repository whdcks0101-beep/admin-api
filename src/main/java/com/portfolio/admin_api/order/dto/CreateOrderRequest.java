package com.portfolio.admin_api.order.dto;

import java.util.List;
import com.portfolio.admin_api.order.application.command.CreateOrderCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "주문 생성 요청")
public record CreateOrderRequest(
		
        @Schema(
                description = "주문할 상품 목록. 최소 한 항목이 필요하며, "
                        + "같은 상품은 한 번만 포함할 수 있습니다."
        )
        @NotEmpty(message = "주문할 상품을 하나 이상 입력해 주세요.")
        List<@NotNull @Valid OrderItemRequest> items
        
) {
	public CreateOrderCommand toCommand() {
	    List<CreateOrderCommand.Item> commandItems = items.stream()
	            .map(item -> new CreateOrderCommand.Item(
	                    item.productId(),
	                    item.quantity()))
	            .toList();

	    return new CreateOrderCommand(commandItems);
	}
}
