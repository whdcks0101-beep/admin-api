package com.portfolio.admin_api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.portfolio.admin_api.inventory.dto.CreateProductRequest;
import com.portfolio.admin_api.inventory.dto.InventoryResponse;
import com.portfolio.admin_api.inventory.entity.Inventory;
import com.portfolio.admin_api.inventory.repository.InventoryRepository;
import com.portfolio.admin_api.inventory.service.InventoryService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventory-lock-test;MODE=MySQL"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryOptimisticLockTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    @DisplayName("이전 버전의 재고 수정은 낙관적 락으로 거부하고 먼저 완료된 변경만 유지한다")
    void staleInventoryUpdateFailsWithOptimisticLock() {
        InventoryResponse created = inventoryService.createProduct(
                new CreateProductRequest(
                        "LOCK-TEST", "동시 수정 테스트 상품", 0, 5
                )
        );

        Inventory before = inventoryRepository
                .findByProduct_Id(created.productId())
                .orElseThrow();

        Long inventoryId = before.getId();
        Long originalVersion = before.getVersion();

        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();

        try {
            first.getTransaction().begin();
            second.getTransaction().begin();

            // 1. 두 작업이 같은 재고와 버전을 읽음
            Inventory firstInventory =
                    first.find(Inventory.class, inventoryId);

            Inventory secondInventory =
                    second.find(Inventory.class, inventoryId);

            assertThat(firstInventory.getQuantity()).isEqualTo(5);
            assertThat(secondInventory.getQuantity()).isEqualTo(5);
            assertThat(secondInventory.getVersion())
                    .isEqualTo(firstInventory.getVersion());

            // 2. 각자 자신의 객체에서 재고를 차감
            firstInventory.decrease(3);
            secondInventory.decrease(2);

            // 3. 첫 번째 작업은 저장 성공
            first.getTransaction().commit();

            // 4. 두 번째 작업은 이전 버전이므로 저장 실패
            assertThatThrownBy(() -> second.flush())
                    .isInstanceOf(OptimisticLockException.class);
        } finally {
            rollbackAndClose(second);
            rollbackAndClose(first);
        }

        // 5. DB에는 첫 번째 작업의 변경만 남음
        Inventory after = inventoryRepository
                .findByProduct_Id(created.productId())
                .orElseThrow();

        assertThat(after.getQuantity()).isEqualTo(2);
        assertThat(after.getVersion()).isEqualTo(originalVersion + 1);
    }

    private void rollbackAndClose(EntityManager entityManager) {
        try {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
        } finally {
            entityManager.close();
        }
    }
}
