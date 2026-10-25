package com.portfolio.admin_api.inventory.dto;

import com.portfolio.admin_api.inventory.entity.Inventory;

public record InventoryResponse(
        Long productId,
        String sku,
        String productName,
        int quantity,
        int safetyStock,
        boolean belowSafetyStock
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getQuantity(),
                inventory.getProduct().getSafetyStock(),
                inventory.isBelowSafetyStock()
        );
    }
}
