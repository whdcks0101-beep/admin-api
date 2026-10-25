package com.portfolio.admin_api.config;

import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

public class ResilientRedisCache extends RedisCache {

	private final CacheErrorHandler errorHandler;

	public ResilientRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration configuration,
			CacheErrorHandler errorHandler) {
		super(name, cacheWriter, configuration);
		this.errorHandler = errorHandler;
	}

	@Override
	public void put(Object key, Object value) {
		try {
			super.put(key, value);
		} catch (RuntimeException exception) {
			errorHandler.handleCachePutError(exception, this, key, value);
		}
	}
}
