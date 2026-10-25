package com.portfolio.admin_api.inventory.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.inventory.dto.CreateProductRequest;
import com.portfolio.admin_api.inventory.dto.InventoryResponse;
import com.portfolio.admin_api.inventory.dto.StockChangeRequest;
import com.portfolio.admin_api.inventory.entity.Inventory;
import com.portfolio.admin_api.inventory.repository.InventoryRepository;
import com.portfolio.admin_api.product.entity.Product;
import com.portfolio.admin_api.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public InventoryResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new IllegalArgumentException("이미 사용 중인 상품 코드입니다.");
        }

        Product product = productRepository.save(
                new Product(request.sku(), request.name(), request.safetyStock())
        );
        Inventory inventory = inventoryRepository.save(new Inventory(product, request.initialQuantity()));

        return InventoryResponse.from(inventory);
    }

    public List<InventoryResponse> getInventories() {
        return inventoryRepository.findAllByOrderByProduct_NameAsc().stream()
                .map(InventoryResponse::from)
                .toList();
    }

    @Transactional
    public InventoryResponse stockIn(Long productId, StockChangeRequest request) {
        Inventory inventory = findInventory(productId);
        inventory.increase(request.quantity());
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse stockOut(Long productId, StockChangeRequest request) {
        Inventory inventory = findInventory(productId);
        inventory.decrease(request.quantity());
        return InventoryResponse.from(inventory);
    }

    private Inventory findInventory(Long productId) {
        return inventoryRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new NoSuchElementException("상품을 찾을 수 없습니다. id=" + productId));
    }
}
