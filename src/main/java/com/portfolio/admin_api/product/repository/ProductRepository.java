package com.portfolio.admin_api.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portfolio.admin_api.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);
}
