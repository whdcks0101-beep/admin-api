package com.portfolio.admin_api.product.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.portfolio.admin_api.outbox.entity.OutboxEvent;

import tools.jackson.databind.json.JsonMapper;

class ProductCacheInvalidationSenderTest {

	private final CacheManager cacheManager = mock(CacheManager.class);
	private final Cache cache = mock(Cache.class);

	private final ProductCacheInvalidationSender sender = new ProductCacheInvalidationSender(cacheManager,
			JsonMapper.builder().build());

	@Test
	@DisplayName("이벤트의 상품 번호에 해당하는 캐시를 삭제한다")
	void evictsProductCache() {
		when(cacheManager.getCache("products")).thenReturn(cache);
		when(cache.evictIfPresent(10L)).thenReturn(true);

		assertThatCode(() -> sender.send(createEvent())).doesNotThrowAnyException();

		verify(cache).evictIfPresent(10L);
	}

	@Test
	@DisplayName("상품 캐시가 이미 없어도 정상적으로 완료한다")
	void succeedsWhenCacheEntryIsAlreadyAbsent() {
		when(cacheManager.getCache("products")).thenReturn(cache);
		when(cache.evictIfPresent(10L)).thenReturn(false);

		assertThatCode(() -> sender.send(createEvent())).doesNotThrowAnyException();

		verify(cache).evictIfPresent(10L);
	}

	@Test
	@DisplayName("Redis 삭제 실패는 재시도할 수 있도록 호출자에게 전달한다")
	void propagatesCacheEvictionFailure() {
		when(cacheManager.getCache("products")).thenReturn(cache);

		var failure = new RedisConnectionFailureException("테스트용 Redis 연결 실패");

		when(cache.evictIfPresent(10L)).thenThrow(failure);

		assertThatThrownBy(() -> sender.send(createEvent())).isSameAs(failure);

		verify(cache).evictIfPresent(10L);
	}

	private OutboxEvent createEvent() {
		return OutboxEvent.create("PRODUCT_CACHE_INVALIDATION_REQUESTED", 10L, """
				{"productId":10}
				""", LocalDateTime.of(2026, 10, 24, 10, 0));
	}
}