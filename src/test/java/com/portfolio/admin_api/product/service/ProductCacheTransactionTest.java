package com.portfolio.admin_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.portfolio.admin_api.outbox.entity.OutboxStatus;
import com.portfolio.admin_api.outbox.repository.OutboxEventRepository;
import com.portfolio.admin_api.product.dto.ProductResponse;
import com.portfolio.admin_api.product.dto.UpdateProductRequest;
import com.portfolio.admin_api.product.entity.Product;
import com.portfolio.admin_api.product.event.ProductCacheInvalidationEvent;
import com.portfolio.admin_api.product.repository.ProductRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:product-cache-tx-test;MODE=MySQL",
		"app.outbox.enabled=false" })
@ActiveProfiles("test")
@Import(ProductCacheTransactionTest.CacheTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductCacheTransactionTest {

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private Cache productsCache;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private ObjectMapper objectMapper;
	
	@Autowired
	private PlatformTransactionManager transactionManager;

	@TestConfiguration(proxyBeanMethods = false)
	@EnableCaching
	static class CacheTestConfig {

		@Bean
		Cache productsCache() {
			return spy(new ConcurrentMapCache("products"));
		}

		@Bean
		CacheManager cacheManager(Cache productsCache) {
			var manager = new SimpleCacheManager();
			manager.setCaches(List.of(productsCache));
			manager.afterPropertiesSet();

			return new TransactionAwareCacheManagerProxy(manager);
		}
	}

	@Test
	@DisplayName("상품 수정과 캐시 삭제 요청이 저장되고 캐시는 즉시 삭제하지 않는다")
	void updateProductStoresCacheInvalidationEvent() {
		Product product = productRepository.save(new Product("CACHE-TX-001", "수정 전 상품", 5));

		Long productId = product.getId();

		// 수정 전 데이터를 캐시에 저장한다.
		productsCache.put(productId, ProductResponse.from(product));

		ProductResponse response = productService.updateProduct(productId, new UpdateProductRequest("수정 후 상품", 10));

		assertThat(response.name()).isEqualTo("수정 후 상품");

		// 서비스 호출이 끝난 뒤 DB에 수정 결과가 저장됐는지 확인한다.
		Product reloaded = productRepository.findById(productId).orElseThrow();

		assertThat(reloaded.getName()).isEqualTo("수정 후 상품");
		assertThat(reloaded.getSafetyStock()).isEqualTo(10);

		// 해당 상품의 캐시 삭제 요청을 찾는다.
		var events = outboxEventRepository.findAll().stream()
				.filter(event -> event.getEventType().equals("PRODUCT_CACHE_INVALIDATION_REQUESTED"))
				.filter(event -> event.getAggregateId().equals(productId)).toList();

		assertThat(events).hasSize(1);

		var event = events.get(0);

		assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

		var payload = objectMapper.readValue(event.getPayload(), ProductCacheInvalidationEvent.class);

		assertThat(payload.productId()).isEqualTo(productId);

		// 상품 수정 서비스는 캐시를 직접 삭제하지 않는다.
		verify(productsCache, never()).evict(productId);
		verify(productsCache, never()).evictIfPresent(productId);

		// 스케줄러를 꺼놨으므로 아직 캐시에는 이전 값이 남아 있다.
		ProductResponse cached = productsCache.get(productId, ProductResponse.class);

		assertThat(cached).isNotNull();
		assertThat(cached.name()).isEqualTo("수정 전 상품");
		assertThat(cached.safetyStock()).isEqualTo(5);
	}
	
	@Test
	@DisplayName("상품 수정 트랜잭션이 롤백되면 상품 변경과 Outbox 저장이 모두 취소된다")
	void rollbackCancelsProductUpdateAndOutboxEvent() {
	    // 테스트 대상 상품은 먼저 커밋해 둔다.
	    Product product = productRepository.save(
	            new Product("CACHE-TX-ROLLBACK", "수정 전 상품", 5));

	    Long productId = product.getId();
	    long beforeCount = outboxEventRepository.count();

	    var transactionTemplate = new TransactionTemplate(transactionManager);

	    transactionTemplate.executeWithoutResult(status -> {
	        // updateProduct는 여기서 시작한 트랜잭션에 참여한다.
	        productService.updateProduct(
	                productId, new UpdateProductRequest("취소될 상품명", 10));

	        Product changed = productRepository.findById(productId).orElseThrow();

	        assertThat(changed.getName()).isEqualTo("취소될 상품명");
	        assertThat(changed.getSafetyStock()).isEqualTo(10);

	        // 트랜잭션 안에서는 Outbox 요청이 저장된 것을 확인할 수 있다.
	        assertThat(outboxEventRepository.count()).isEqualTo(beforeCount + 1);

	        // 정상 종료하더라도 커밋하지 않고 롤백하도록 지정한다.
	        status.setRollbackOnly();
	    });

	    // 트랜잭션이 종료된 뒤 다시 조회한다.
	    Product reloaded = productRepository.findById(productId).orElseThrow();

	    assertThat(reloaded.getName()).isEqualTo("수정 전 상품");
	    assertThat(reloaded.getSafetyStock()).isEqualTo(5);

	    // Outbox 저장도 함께 취소돼야 한다.
	    assertThat(outboxEventRepository.count()).isEqualTo(beforeCount);
	}	
	
}