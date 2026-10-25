package com.portfolio.admin_api.product.dto;

import com.portfolio.admin_api.product.entity.Product;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        int safetyStock
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSafetyStock()
        );
    }
}
