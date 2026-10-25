package com.portfolio.admin_api.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.portfolio.admin_api.order.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long>{
    @Query("""
            select oi
            from OrderItem oi
            join fetch oi.product
            where oi.order.id = :orderId
            order by oi.id asc
            """
    	   )
    List<OrderItem> findAllWithProductByOrderId(
            @Param("orderId") Long orderId
    );
}
