package com.portfolio.admin_api.order.statistics.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.portfolio.admin_api.order.statistics.entity.OrderStatistic;

public interface OrderStatisticRepository extends JpaRepository<OrderStatistic, Long> {
	
	 @Modifying
	    @Query(value = """
	            INSERT INTO order_statistics (order_id, ordered_at)
	            VALUES (:orderId, :orderedAt)
	            ON DUPLICATE KEY UPDATE order_id = order_id
	            """, nativeQuery = true)
	    void insertIfAbsent(
	            @Param("orderId") Long orderId,
	            @Param("orderedAt") LocalDateTime orderedAt
	    );
	
}