package com.portfolio.admin_api.order.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.portfolio.admin_api.order.dto.OrderSearchRequest;
import com.portfolio.admin_api.order.entity.Order;
import com.portfolio.admin_api.order.entity.QOrder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {
	
	private final JPAQueryFactory queryFactory;
	
	 public Page<Order> search(OrderSearchRequest request,Pageable pageable) {
        QOrder order = QOrder.order;

        BooleanExpression[] conditions = {
                orderIdEq(request.getOrderId()),
                createdAtGoe(request.getFromDate()),
                createdAtBeforeNextDay(request.getToDate())
        };

        List<Order> content = queryFactory
                .selectFrom(order)
                .where(conditions)
                .orderBy(order.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(order.count())
                .from(order)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(
                content,
                pageable,
                total != null ? total : 0L
        );
	 }
	 
    private BooleanExpression orderIdEq(Long orderId) {
        if (orderId == null) {
            return null;
        }

        return QOrder.order.id.eq(orderId);
    }
    
    private BooleanExpression createdAtGoe(LocalDate fromDate) {
        if (fromDate == null) {
            return null;
        }

        return QOrder.order.createdAt.goe(
                fromDate.atStartOfDay()
        );
    }

    private BooleanExpression createdAtBeforeNextDay(LocalDate toDate) {
        if (toDate == null) {
            return null;
        }

        return QOrder.order.createdAt.lt(
                toDate.plusDays(1).atStartOfDay()
        );
    }
    
}
