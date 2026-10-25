package com.portfolio.admin_api.product.service;

import java.util.NoSuchElementException;
import com.portfolio.admin_api.product.event.ProductCacheInvalidationEvent;
import com.portfolio.admin_api.product.event.ProductCacheOutboxWriter;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.product.dto.ProductResponse;
import com.portfolio.admin_api.product.dto.UpdateProductRequest;
import com.portfolio.admin_api.product.entity.Product;
import com.portfolio.admin_api.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;
	private final ProductCacheOutboxWriter productCacheOutboxWriter;

	@Cacheable(cacheNames = "products", key = "#productId")
	public ProductResponse getProduct(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new NoSuchElementException("해당 상품이 없습니다. id=" + productId));

		return ProductResponse.from(product);
	}

	@Transactional
	public ProductResponse updateProduct(Long productId, UpdateProductRequest request) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new NoSuchElementException("해당 상품이 없습니다. id=" + productId));

		product.updateInfo(request.name(), request.safetyStock());

		productCacheOutboxWriter.save(new ProductCacheInvalidationEvent(productId));

		return ProductResponse.from(product);
	}

	public ProductResponse getProductForEdit(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new NoSuchElementException("해당 상품이 없습니다. id=" + productId));

		return ProductResponse.from(product);
	}

}
