package com.portfolio.admin_api.product.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.admin_api.product.dto.ProductResponse;
import com.portfolio.admin_api.product.dto.UpdateProductRequest;
import com.portfolio.admin_api.product.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ProductResponse getProduct(
            @PathVariable("productId") Long productId
    ) {
        return productService.getProduct(productId);
    }
    
    @PutMapping("/{productId}")
    public ProductResponse updateProduct(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.updateProduct(productId, request);
    }
    
    @GetMapping("/{productId}/edit")
    public ProductResponse getProductForEdit(
            @PathVariable("productId") Long productId
    ) {
        return productService.getProductForEdit(productId);
    }
    
}
