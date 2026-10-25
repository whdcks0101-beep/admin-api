package com.portfolio.admin_api.common.ratelimit;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisRateLimiter {

	private final StringRedisTemplate redisTemplate;

	@SuppressWarnings("rawtypes")
	private final DefaultRedisScript<List> script;

	public RedisRateLimiter(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;

		this.script = new DefaultRedisScript<>();
		this.script.setLocation(new ClassPathResource("redis/rate-limit.lua"));
		this.script.setResultType(List.class);
	}

	public RateLimitResult tryAcquire(String key, int limit, int windowSeconds) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("요청 제한 키는 필수입니다.");
		}

		if (limit <= 0 || windowSeconds <= 0) {
			throw new IllegalArgumentException("허용 횟수와 제한 시간은 1 이상이어야 합니다.");
		}

		List<?> result = redisTemplate.execute(script, List.of(key), String.valueOf(limit),
				String.valueOf(windowSeconds));

		if (result == null || result.size() != 3) {
			throw new IllegalStateException("요청 제한 스크립트의 응답이 올바르지 않습니다.");
		}

		long allowed = ((Number) result.get(0)).longValue();
		long remaining = ((Number) result.get(1)).longValue();
		long retryAfter = ((Number) result.get(2)).longValue();

		return new RateLimitResult(allowed == 1, remaining, retryAfter);
	}

}
