package com.portfolio.admin_api.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.portfolio.admin_api.inventory.dto.CreateProductRequest;
import com.portfolio.admin_api.inventory.dto.InventoryResponse;
import com.portfolio.admin_api.inventory.repository.InventoryRepository;
import com.portfolio.admin_api.inventory.service.InventoryService;
import com.portfolio.admin_api.order.application.command.CreateOrderCommand;
import com.portfolio.admin_api.order.repository.OrderItemRepository;
import com.portfolio.admin_api.order.repository.OrderRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-rollback-test;MODE=MySQL"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderServiceRollbackTest {
	@Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;
    
    @Test
    @DisplayName("주문 중 재고가 부족하면 모든 재고 차감과 주문·주문 항목 저장을 롤백한다")
    void insufficientStockRollsBackEntireOrder() {
        // given: 상품과 초기 재고는 미리 저장 완료
        InventoryResponse first = inventoryService.createProduct(
                new CreateProductRequest(
                        "ROLLBACK-A", "롤백 테스트 상품 A", 0, 10
                )
        );

        InventoryResponse second = inventoryService.createProduct(
                new CreateProductRequest(
                        "ROLLBACK-B", "롤백 테스트 상품 B", 0, 1
                )
        );

        long orderCountBefore = orderRepository.count();
        long itemCountBefore = orderItemRepository.count();

        CreateOrderCommand command = new CreateOrderCommand(
                List.of(
                        new CreateOrderCommand.Item(first.productId(), 3),
                        new CreateOrderCommand.Item(second.productId(), 2)
                )
        );

        // when: 두 번째 상품에서 재고 부족 발생
        assertThatThrownBy(() -> orderService.createOrder(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("재고가 부족합니다.");

        // then: 서비스의 트랜잭션 종료 후 DB에서 다시 확인
        int firstQuantity = inventoryRepository
                .findByProduct_Id(first.productId())
                .orElseThrow()
                .getQuantity();

        int secondQuantity = inventoryRepository
                .findByProduct_Id(second.productId())
                .orElseThrow()
                .getQuantity();

        assertThat(firstQuantity).isEqualTo(10);
        assertThat(secondQuantity).isEqualTo(1);

        assertThat(orderRepository.count()).isEqualTo(orderCountBefore);
        assertThat(orderItemRepository.count()).isEqualTo(itemCountBefore);
    }
}
