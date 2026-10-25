package com.portfolio.admin_api.order.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.portfolio.admin_api.order.entity.OrderItem;
import com.portfolio.admin_api.order.entity.QOrderItem;
import com.portfolio.admin_api.product.entity.QProduct;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderItemQueryRepository {
	
	private final JPAQueryFactory queryFactory;

	
    public List<OrderItem> findAllWithProductByOrderId(Long orderId) {
        QOrderItem orderItem = QOrderItem.orderItem;
        QProduct product = QProduct.product;

        return queryFactory
                .selectFrom(orderItem)
                .join(orderItem.product, product).fetchJoin()
                .where(orderItem.order.id.eq(orderId))
                .orderBy(orderItem.id.asc())
                .fetch();
    }

}
