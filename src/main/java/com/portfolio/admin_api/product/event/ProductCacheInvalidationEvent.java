package com.portfolio.admin_api.product.event;

public record ProductCacheInvalidationEvent(Long productId) {
	public ProductCacheInvalidationEvent {
		if (productId == null || productId <= 0) {
			throw new IllegalArgumentException("상품 번호는 양수여야 합니다.");
		}
	}
}
