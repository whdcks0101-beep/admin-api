package com.portfolio.admin_api.product.event;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.portfolio.admin_api.outbox.config.OutboxProperties;
import com.portfolio.admin_api.outbox.entity.OutboxEvent;
import com.portfolio.admin_api.outbox.sender.OutboxEventSenderRegistry;
import com.portfolio.admin_api.outbox.service.OutboxClaimService;
import com.portfolio.admin_api.outbox.service.OutboxRelay;
import com.portfolio.admin_api.outbox.service.OutboxStatusService;

import tools.jackson.databind.json.JsonMapper;

class ProductCacheInvalidationRetryTest {

	@Test
	@DisplayName("캐시 삭제 실패 시 재시도를 예약하고 재시도 성공 시 완료 처리한다")
	void retriesFailedCacheInvalidation() {
		CacheManager cacheManager = mock(CacheManager.class);
		Cache cache = mock(Cache.class);

		OutboxClaimService claimService = mock(OutboxClaimService.class);
		OutboxStatusService statusService = mock(OutboxStatusService.class);

		var sender = new ProductCacheInvalidationSender(cacheManager, JsonMapper.builder().build());

		var registry = new OutboxEventSenderRegistry(List.of(sender));

		var properties = new OutboxProperties();
		properties.setMaxEventsPerRun(1);

		var relay = new OutboxRelay(claimService, registry, statusService, properties);

		// 같은 Outbox 요청을 두 번 선점한 상황이다.
		// 재선점할 때는 새로운 토큰을 사용한다.
		OutboxEvent firstClaim = claimedEvent("first-token");
		OutboxEvent retryClaim = claimedEvent("retry-token");

		when(claimService.claimNext()).thenReturn(Optional.of(firstClaim)).thenReturn(Optional.of(retryClaim));

		when(cacheManager.getCache("products")).thenReturn(cache);

		// 첫 삭제는 실패하고, 두 번째 삭제는 성공한다.
		when(cache.evictIfPresent(10L)).thenThrow(new RedisConnectionFailureException("테스트용 일시적인 Redis 장애"))
				.thenReturn(true);

		when(statusService.recordFailure(100L, "first-token")).thenReturn(true);

		when(statusService.markPublished(100L, "retry-token")).thenReturn(true);

		// 첫 실행: 실패를 기록하고 재시도를 예약한다.
		relay.publishPending();

		verify(statusService).recordFailure(100L, "first-token");
		verify(statusService, never()).markPublished(100L, "first-token");

		// 재시도 시간이 지나 다시 선점됐다고 가정하고 실행한다.
		relay.publishPending();

		// 실패 → 재시도 예약 → 삭제 성공 → 완료 처리 순서를 확인한다.
		var ordered = inOrder(cache, statusService);

		ordered.verify(cache).evictIfPresent(10L);
		ordered.verify(statusService).recordFailure(100L, "first-token");

		ordered.verify(cache).evictIfPresent(10L);
		ordered.verify(statusService).markPublished(100L, "retry-token");

		verify(statusService, never()).recordFailure(100L, "retry-token");
	}

	private OutboxEvent claimedEvent(String token) {
		OutboxEvent event = mock(OutboxEvent.class);

		when(event.getId()).thenReturn(100L);
		when(event.getEventType()).thenReturn("PRODUCT_CACHE_INVALIDATION_REQUESTED");
		when(event.getAggregateId()).thenReturn(10L);
		when(event.getClaimToken()).thenReturn(token);
		when(event.getPayload()).thenReturn("""
				{"productId":10}
				""");

		return event;
	}
}
