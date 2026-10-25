package com.portfolio.admin_api.config;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProductCacheErrorHandler extends SimpleCacheErrorHandler {
	@Override
	public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
		if (canIgnore(exception, cache)) {
			log.warn("상품 캐시 조회 실패. DB 조회로 진행합니다. cache={}, errorType={}", cache.getName(),
					exception.getClass().getSimpleName());
			return;
		}

		throw exception;
	}

	@Override
	public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
		if (canIgnore(exception, cache)) {
			log.warn("상품 캐시 저장 실패. 조회 결과는 반환합니다. cache={}, errorType={}", cache.getName(),
					exception.getClass().getSimpleName());
			return;
		}

		throw exception;
	}

	private boolean canIgnore(RuntimeException exception, Cache cache) {
		boolean productCache = "products".equals(cache.getName());

		boolean connectionProblem = exception instanceof RedisConnectionFailureException
				|| exception instanceof QueryTimeoutException;

		return productCache && connectionProblem;
	}
}
