package com.portfolio.admin_api.inventory.entity;

import com.portfolio.admin_api.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Version
    @Column(nullable = false)
    private Long version;

    public Inventory(Product product, int initialQuantity) {
        if (initialQuantity < 0) {
            throw new IllegalArgumentException("초기 재고는 0 이상이어야 합니다.");
        }

        this.product = product;
        this.quantity = initialQuantity;
    }

    public void increase(int amount) {
        validateAmount(amount);
        quantity += amount;
    }

    public void decrease(int amount) {
        validateAmount(amount);
        if (quantity < amount) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        quantity -= amount;
    }

    public boolean isBelowSafetyStock() {
        return quantity <= product.getSafetyStock();
    }

    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }
}
