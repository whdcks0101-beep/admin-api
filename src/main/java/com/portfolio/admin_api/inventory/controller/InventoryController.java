package com.portfolio.admin_api.inventory.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.admin_api.inventory.dto.CreateProductRequest;
import com.portfolio.admin_api.inventory.dto.InventoryResponse;
import com.portfolio.admin_api.inventory.dto.StockChangeRequest;
import com.portfolio.admin_api.inventory.service.InventoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        return inventoryService.createProduct(request);
    }

    @GetMapping("/inventories")
    public List<InventoryResponse> getInventories() {
        return inventoryService.getInventories();
    }

    @PostMapping("/products/{productId}/stock-in")
    public InventoryResponse stockIn(
    		@PathVariable("productId") Long productId,
            @Valid @RequestBody StockChangeRequest request
    ) {
        return inventoryService.stockIn(productId, request);
    }

    @PostMapping("/products/{productId}/stock-out")
    public InventoryResponse stockOut(
    		@PathVariable("productId") Long productId,
            @Valid @RequestBody StockChangeRequest request
    ) {
        return inventoryService.stockOut(productId, request);
    }
}
