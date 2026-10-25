package com.portfolio.admin_api.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRateLimiterTest {

	private static final String KEY = "admin-api:rate-limit:orders:user:8";

	@Test
	@DisplayName("Redis가 요청을 허용하면 남은 요청 횟수를 반환한다")
	void allowedRequestReturnsRemainingCount() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

		RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate);

		when(redisTemplate.execute(any(), eq(List.of(KEY)), eq("5"), eq("60"))).thenReturn(List.of(1L, 4L, 0L));

		RateLimitResult result = limiter.tryAcquire(KEY, 5, 60);

		assertThat(result.allowed()).isTrue();
		assertThat(result.remainingRequests()).isEqualTo(4);
		assertThat(result.retryAfterSeconds()).isZero();
	}

	@Test
	@DisplayName("Redis가 요청을 거절하면 재시도 대기 시간을 반환한다")
	void rejectedRequestReturnsRetryAfter() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

		RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate);

		when(redisTemplate.execute(any(), eq(List.of(KEY)), eq("5"), eq("60"))).thenReturn(List.of(0L, 0L, 23L));

		RateLimitResult result = limiter.tryAcquire(KEY, 5, 60);

		assertThat(result.allowed()).isFalse();
		assertThat(result.remainingRequests()).isZero();
		assertThat(result.retryAfterSeconds()).isEqualTo(23);
	}

}
