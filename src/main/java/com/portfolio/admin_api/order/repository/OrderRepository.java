package com.portfolio.admin_api.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portfolio.admin_api.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long>{

}
