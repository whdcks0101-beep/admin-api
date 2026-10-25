package com.portfolio.admin_api.product.event;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.portfolio.admin_api.outbox.entity.OutboxEvent;
import com.portfolio.admin_api.outbox.sender.OutboxEventSender;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("local")
@RequiredArgsConstructor
public class ProductCacheInvalidationSender implements OutboxEventSender {

	private final CacheManager cacheManager;
	private final ObjectMapper objectMapper;

	@Override
	public String supportedEventType() {
		return "PRODUCT_CACHE_INVALIDATION_REQUESTED";
	}

	@Override
	public void send(OutboxEvent outboxEvent) {
		if (!supportedEventType().equals(outboxEvent.getEventType())) {
			throw new IllegalArgumentException("지원하지 않는 이벤트 유형입니다: " + outboxEvent.getEventType());
		}

		ProductCacheInvalidationEvent event = objectMapper.readValue(outboxEvent.getPayload(),
				ProductCacheInvalidationEvent.class);

		if (!event.productId().equals(outboxEvent.getAggregateId())) {
			throw new IllegalArgumentException("이벤트의 상품 번호와 대상 ID가 일치하지 않습니다.");
		}

		Cache cache = cacheManager.getCache("products");

		if (cache == null) {
			throw new IllegalStateException("products 캐시가 등록되어 있지 않습니다.");
		}

		cache.evictIfPresent(event.productId());
	}
}