package com.portfolio.admin_api.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.portfolio.admin_api.user.entity.User;
import com.portfolio.admin_api.inventory.entity.Inventory;
import com.portfolio.admin_api.inventory.repository.InventoryRepository;
import com.portfolio.admin_api.product.entity.Product;
import com.portfolio.admin_api.product.repository.ProductRepository;
import com.portfolio.admin_api.user.repository.UserRepository;

@Configuration
@Profile("local")
public class DataLoader {

	@Bean
	public CommandLineRunner initData(ProductRepository productRepository, InventoryRepository inventoryRepository) {
		return args -> {
			if (productRepository.count() == 0) {
				Product keyboard = productRepository.save(new Product("KEYBOARD-001", "기계식 키보드", 10));

				Product mouse = productRepository.save(new Product("MOUSE-001", "무선 마우스", 15));

				Product monitor = productRepository.save(new Product("MONITOR-001", "27인치 모니터", 5));

				inventoryRepository.save(new Inventory(keyboard, 20));
				inventoryRepository.save(new Inventory(mouse, 8));
				inventoryRepository.save(new Inventory(monitor, 5));
			}
		};
	}
}
