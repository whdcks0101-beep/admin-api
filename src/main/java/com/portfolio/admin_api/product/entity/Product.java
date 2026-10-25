package com.portfolio.admin_api.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String sku;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    public Product(String sku, String name, int safetyStock) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("상품 코드는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        if (safetyStock < 0) {
            throw new IllegalArgumentException("안전 재고는 0 이상이어야 합니다.");
        }

        this.sku = sku;
        this.name = name;
        this.safetyStock = safetyStock;
    }
    
    public void updateInfo(String name, int safetyStock) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }

        if (name.length() > 100) {
            throw new IllegalArgumentException(
                    "상품명은 100자 이하여야 합니다."
            );
        }

        if (safetyStock < 0) {
            throw new IllegalArgumentException(
                    "안전 재고는 0 이상이어야 합니다."
            );
        }

        this.name = name;
        this.safetyStock = safetyStock;
    }
    
}
