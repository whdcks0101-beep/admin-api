package com.portfolio.admin_api.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class RedisRateLimiterIntegrationTest {

	@Test
	@DisplayName("서로 다른 연결에서 같은 키로 요청하면 허용 횟수를 공유한다")
	void sharesLimitAcrossIndependentConnections() {
		var connectionA = new LettuceConnectionFactory("localhost", 6379);
		var connectionB = new LettuceConnectionFactory("localhost", 6379);

		try {
			connectionA.afterPropertiesSet();
			connectionA.start();

			connectionB.afterPropertiesSet();
			connectionB.start();

			var templateA = new StringRedisTemplate(connectionA);
			var templateB = new StringRedisTemplate(connectionB);

			var limiterA = new RedisRateLimiter(templateA);
			var limiterB = new RedisRateLimiter(templateB);

			// 실제 서비스 키와 구분하고, 테스트마다 새로운 키를 사용한다.
			String key = "test:rate-limit:shared:" + UUID.randomUUID();

			// A에서 3회 사용
			for (int i = 0; i < 3; i++) {
				assertThat(limiterA.tryAcquire(key, 5, 60).allowed()).isTrue();
			}

			// B에서도 남은 2회만 사용할 수 있다.
			for (int i = 0; i < 2; i++) {
				assertThat(limiterB.tryAcquire(key, 5, 60).allowed()).isTrue();
			}

			RateLimitResult rejected = limiterB.tryAcquire(key, 5, 60);

			assertThat(rejected.allowed()).isFalse();
			assertThat(rejected.remainingRequests()).isZero();
			assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);

			// A로 돌아가더라도 이미 공통 한도를 소진했다.
			assertThat(limiterA.tryAcquire(key, 5, 60).allowed()).isFalse();
		} finally {
			try {
				connectionA.destroy();
			} finally {
				connectionB.destroy();
			}
		}
	}

	@Test
	@DisplayName("요청 제한 키가 만료되면 다시 요청을 허용한다")
	void allowsRequestsAgainAfterExpiration() throws Exception {
		var connection = new LettuceConnectionFactory("localhost", 6379);

		try {
			connection.afterPropertiesSet();
			connection.start();

			var template = new StringRedisTemplate(connection);
			var limiter = new RedisRateLimiter(template);

			String key = "test:rate-limit:expiry:" + UUID.randomUUID();

			// 한 번만 허용하고, 두 번째 요청은 거절한다.
			assertThat(limiter.tryAcquire(key, 1, 60).allowed()).isTrue();
			assertThat(limiter.tryAcquire(key, 1, 60).allowed()).isFalse();

			// Lua가 실제로 만료 시간을 설정했는지 확인한다.
			assertThat(template.getExpire(key, TimeUnit.MILLISECONDS)).isPositive().isLessThanOrEqualTo(60_000L);

			// 테스트에서 60초를 기다리지 않도록 이 키의 TTL만 줄인다.
			assertThat(template.expire(key, Duration.ofMillis(100))).isTrue();

			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

			while (Boolean.TRUE.equals(template.hasKey(key)) && System.nanoTime() < deadline) {
				Thread.sleep(25);
			}

			assertThat(template.hasKey(key)).isFalse();

			// 만료 후에는 새 제한 구간이 시작된다.
			RateLimitResult result = limiter.tryAcquire(key, 1, 60);

			assertThat(result.allowed()).isTrue();
			assertThat(result.remainingRequests()).isZero();

			// 새로 만들어진 키에도 TTL이 있어야 한다.
			assertThat(template.getExpire(key, TimeUnit.MILLISECONDS)).isPositive().isLessThanOrEqualTo(60_000L);
		} finally {
			connection.destroy();
		}
	}

}
