package com.portfolio.admin_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import com.portfolio.admin_api.product.event.ProductCacheInvalidationEvent;
import com.portfolio.admin_api.product.event.ProductCacheOutboxWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.portfolio.admin_api.config.ProductCacheErrorHandler;
import com.portfolio.admin_api.product.dto.ProductResponse;
import com.portfolio.admin_api.product.entity.Product;
import com.portfolio.admin_api.product.repository.ProductRepository;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Duration;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

import com.portfolio.admin_api.config.ResilientRedisCache;
import com.portfolio.admin_api.product.dto.UpdateProductRequest;

class ProductCacheFailureTest {

	@Configuration(proxyBeanMethods = false)
	@EnableCaching
	static class TestConfig implements CachingConfigurer {
		
		@Bean
		ProductCacheOutboxWriter productCacheOutboxWriter() {
			return mock(ProductCacheOutboxWriter.class);
		}

		@Bean
		Cache cache() {
			Cache cache = mock(Cache.class);
			when(cache.getName()).thenReturn("products");
			return cache;
		}

		@Bean
		CacheManager cacheManager(Cache cache) {
			CacheManager manager = mock(CacheManager.class);
			when(manager.getCache("products")).thenReturn(cache);
			return manager;
		}

		@Bean
		ProductRepository productRepository() {
			return mock(ProductRepository.class);
		}

		@Bean
		ProductService productService(ProductRepository repository, ProductCacheOutboxWriter writer) {
			return new ProductService(repository, writer);
		}

		@Override
		@Bean
		public CacheErrorHandler errorHandler() {
			return new ProductCacheErrorHandler();
		}

	}

	@Test
	@DisplayName("상품 캐시 조회가 실패해도 Repository에서 상품을 조회하여 반환한다")
	void cacheGetFailureFallsBackToRepository() {
		try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {

			ProductService productService = context.getBean(ProductService.class);

			ProductRepository productRepository = context.getBean(ProductRepository.class);

			Cache cache = context.getBean(Cache.class);

			// 캐시 조회만 실패하도록 설정
			when(cache.get(1L)).thenThrow(new RedisConnectionFailureException("테스트용 Redis 연결 실패"));

			// Repository가 돌려줄 상품
			Product product = new Product("CACHE-TEST-001", "캐시 장애 테스트 상품", 5);

			when(productRepository.findById(1L)).thenReturn(Optional.of(product));

			ProductResponse response = productService.getProduct(1L);

			assertThat(response.sku()).isEqualTo("CACHE-TEST-001");
			assertThat(response.name()).isEqualTo("캐시 장애 테스트 상품");
			assertThat(response.safetyStock()).isEqualTo(5);

			verify(cache).get(1L);
			verify(productRepository).findById(1L);

			// DB 조회 결과를 캐시에 다시 저장하려는 것도 확인
			verify(cache).put(1L, response);
		}
	}

	@Test
	@DisplayName("캐시 조회와 저장이 모두 실패해도 Repository에서 조회한 상품을 반환한다")
	void cacheGetAndPutFailureStillReturnsProduct() {
		try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {

			ProductService productService = context.getBean(ProductService.class);

			ProductRepository productRepository = context.getBean(ProductRepository.class);

			Cache cache = context.getBean(Cache.class);

			Product product = new Product("CACHE-TEST-002", "캐시 저장 장애 테스트 상품", 5);

			ProductResponse expected = ProductResponse.from(product);

			when(cache.get(1L)).thenThrow(new RedisConnectionFailureException("테스트용 캐시 조회 실패"));

			when(productRepository.findById(1L)).thenReturn(Optional.of(product));

			doThrow(new RedisConnectionFailureException("테스트용 캐시 저장 실패")).when(cache).put(1L, expected);

			ProductResponse response = productService.getProduct(1L);
			assertThat(response).isEqualTo(expected);

			verify(productRepository).findById(1L);
			verify(cache).put(1L, expected);
		}
	}

	@Test
	@DisplayName("연결 장애가 아닌 캐시 조회 오류는 숨기지 않고 전달한다")
	void unexpectedCacheErrorIsNotIgnored() {
		try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {

			ProductService productService = context.getBean(ProductService.class);

			ProductRepository productRepository = context.getBean(ProductRepository.class);

			Cache cache = context.getBean(Cache.class);

			IllegalStateException failure = new IllegalStateException("테스트용 예상하지 못한 캐시 오류");

			when(cache.get(1L)).thenThrow(failure);

			assertThatThrownBy(() -> productService.getProduct(1L)).isSameAs(failure);

			verify(cache).get(1L);
			verifyNoInteractions(productRepository);
		}
	}

	@Test
	@DisplayName("상품 캐시 삭제 중 연결 장애가 발생해도 예외를 무시하지 않는다")
	void cacheEvictFailureIsNotIgnored() {
		ProductCacheErrorHandler errorHandler = new ProductCacheErrorHandler();

		Cache cache = mock(Cache.class);

		RedisConnectionFailureException failure = new RedisConnectionFailureException("테스트용 캐시 삭제 실패");

		assertThatThrownBy(() -> errorHandler.handleCacheEvictError(failure, cache, 1L)).isSameAs(failure);
	}

	@Test
	@DisplayName("트랜잭션 연동 캐시 저장은 즉시 실행되지 않고 커밋 후 단계에서 실패한다")
	void deferredCachePutFailsAfterCommit() {
		Cache targetCache = mock(Cache.class);

		var transactionAwareCache = new TransactionAwareCacheDecorator(targetCache);

		ProductResponse response = new ProductResponse(1L, "CACHE-TEST-003", "커밋 후 캐시 테스트 상품", 5);

		RedisConnectionFailureException failure = new RedisConnectionFailureException("테스트용 커밋 후 캐시 저장 실패");

		doThrow(failure).when(targetCache).put(1L, response);

		// 실제 DB 트랜잭션 대신, 콜백을 등록할 테스트 환경을 준비
		TransactionSynchronizationManager.initSynchronization();

		try {
			transactionAwareCache.put(1L, response);

			// 아직 실제 캐시에는 저장하지 않음
			verify(targetCache, never()).put(1L, response);

			var callbacks = TransactionSynchronizationManager.getSynchronizations();

			assertThat(callbacks).hasSize(1);

			// 커밋 이후에 호출될 작업을 테스트에서 직접 실행
			assertThatThrownBy(() -> callbacks.get(0).afterCommit()).isSameAs(failure);

			verify(targetCache).put(1L, response);
		} finally {
			// 다른 테스트에 영향을 주지 않도록 정리
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("커밋 후 상품 캐시 저장이 연결 장애로 실패해도 예외를 전달하지 않는다")
	void deferredCachePutFailureIsHandled() {
		RedisCacheWriter writer = mock(RedisCacheWriter.class);

		Duration ttl = Duration.ofMinutes(5);

		RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl);

		var resilientCache = new ResilientRedisCache("products", writer, configuration, new ProductCacheErrorHandler());

		var transactionAwareCache = new TransactionAwareCacheDecorator(resilientCache);

		doThrow(new RedisConnectionFailureException("테스트용 커밋 후 Redis 저장 실패")).when(writer).put(eq("products"),
				any(byte[].class), any(byte[].class), eq(ttl));

		TransactionSynchronizationManager.initSynchronization();

		try {
			// 이 테스트는 저장 오류 처리가 목적이므로 간단한 문자열 사용
			transactionAwareCache.put(1L, "테스트 상품");

			// 아직 Redis 저장 작업은 실행되지 않음
			verifyNoInteractions(writer);

			var callbacks = TransactionSynchronizationManager.getSynchronizations();

			assertThat(callbacks).hasSize(1);

			// 커밋 후 저장에 실패해도 오류 처리기가 처리하므로 예외가 나오지 않음
			assertThatCode(() -> callbacks.get(0).afterCommit()).doesNotThrowAnyException();

			verify(writer).put(eq("products"), any(byte[].class), any(byte[].class), eq(ttl));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("상품 수정 시 캐시를 직접 삭제하지 않고 Outbox 저장을 요청한다")
	void updateProductRequestsCacheInvalidationThroughOutbox() {
		try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {

			ProductService productService = context.getBean(ProductService.class);

			ProductRepository productRepository = context.getBean(ProductRepository.class);

			ProductCacheOutboxWriter writer = context.getBean(ProductCacheOutboxWriter.class);

			Cache cache = context.getBean(Cache.class);

			Product product = new Product("CACHE-KEY-TEST", "수정 전 상품", 5);

			when(productRepository.findById(8L)).thenReturn(Optional.of(product));

			ProductResponse response = productService.updateProduct(8L, new UpdateProductRequest("수정 후 상품", 10));

			assertThat(response.name()).isEqualTo("수정 후 상품");
			assertThat(response.safetyStock()).isEqualTo(10);

			verify(productRepository).findById(8L);

			verify(writer).save(new ProductCacheInvalidationEvent(8L));

			verifyNoInteractions(cache);
		}
	}

	@Test
	@DisplayName("캐시 삭제는 커밋 이후 실행되며 삭제 실패는 예외로 전달된다")
	void deferredCacheEvictFailsAfterCommit() {
		Cache targetCache = mock(Cache.class);

		var transactionAwareCache = new TransactionAwareCacheDecorator(targetCache);

		RedisConnectionFailureException failure = new RedisConnectionFailureException("테스트용 커밋 후 캐시 삭제 실패");

		doThrow(failure).when(targetCache).evict(1L);

		TransactionSynchronizationManager.initSynchronization();

		try {
			transactionAwareCache.evict(1L);

			// 삭제 요청을 해도 아직 실제 캐시에서는 삭제하지 않는다.
			verify(targetCache, never()).evict(1L);

			var callbacks = TransactionSynchronizationManager.getSynchronizations();

			assertThat(callbacks).hasSize(1);

			// DB 커밋 이후 실행되는 단계를 재현한다.
			assertThatThrownBy(() -> callbacks.get(0).afterCommit()).isSameAs(failure);

			verify(targetCache).evict(1L);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

}
