package com.portfolio.admin_api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.portfolio.admin_api.inventory.dto.CreateProductRequest;
import com.portfolio.admin_api.inventory.dto.InventoryResponse;
import com.portfolio.admin_api.inventory.dto.StockChangeRequest;
import com.portfolio.admin_api.inventory.service.InventoryService;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Test
    @DisplayName("출고 후 안전재고 여부를 확인하고 남은 재고보다 많은 출고는 거부한다")
    void stockOutCannotMakeInventoryNegative() {
        InventoryResponse created = inventoryService.createProduct(
                new CreateProductRequest("TEST-001", "테스트 상품", 2, 5)
        );

        InventoryResponse afterStockOut = inventoryService.stockOut(
                created.productId(), new StockChangeRequest(3)
        );

        assertThat(afterStockOut.quantity()).isEqualTo(2);
        assertThat(afterStockOut.belowSafetyStock()).isTrue();
        assertThatThrownBy(() -> inventoryService.stockOut(
                created.productId(), new StockChangeRequest(3)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("재고가 부족합니다.");
    }
}
