package com.portfolio.admin_api.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.portfolio.admin_api.product.dto.ProductResponse;

@Configuration
@EnableCaching
@Profile("local")
public class RedisCacheConfig implements CachingConfigurer {

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, CacheErrorHandler errorHandler) {
		RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

		RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration.defaultCacheConfig();

		Map<String, RedisCacheConfiguration> configurations = Map.of("products",
				createCacheConfiguration(ProductResponse.class, Duration.ofMinutes(5)));

		RedisCacheManager manager = new RedisCacheManager(writer, defaultConfiguration, false, configurations) {
			@Override
			protected RedisCache createRedisCache(String name, RedisCacheConfiguration configuration) {
				RedisCacheConfiguration resolvedConfiguration = configuration != null ? configuration
						: defaultConfiguration;

				return new ResilientRedisCache(name, writer, resolvedConfiguration, errorHandler);
			}
		};

		manager.setTransactionAware(true);

		return manager;
	}

	private <T> RedisCacheConfiguration createCacheConfiguration(Class<T> valueType, Duration ttl) {
		var serializer = new JacksonJsonRedisSerializer<>(valueType);

		return RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl).disableCachingNullValues()
				.computePrefixWith(name -> "admin-api:cache:" + name + "::")
				.serializeValuesWith(SerializationPair.fromSerializer(serializer));
	}
	
	@Override
	@Bean
	public CacheErrorHandler errorHandler() {
	    return new ProductCacheErrorHandler();
	}

}
