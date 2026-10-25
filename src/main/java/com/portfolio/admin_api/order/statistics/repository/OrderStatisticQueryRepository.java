package com.portfolio.admin_api.order.statistics.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.portfolio.admin_api.order.statistics.dto.DailyOrderStatisticResponse;
import com.portfolio.admin_api.order.statistics.entity.QOrderStatistic;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderStatisticQueryRepository {

	private final JPAQueryFactory queryFactory;

	public List<DailyOrderStatisticResponse> findDailyCounts(LocalDate fromDate, LocalDate toDate) {

		QOrderStatistic statistic = QOrderStatistic.orderStatistic;

		DateExpression<LocalDate> orderDate = Expressions.dateTemplate(LocalDate.class, "cast({0} as LocalDate)",
				statistic.orderedAt);

		return queryFactory
                .select(Projections.constructor(
                        DailyOrderStatisticResponse.class,
                        orderDate,
                        statistic.orderId.count()
                ))
                .from(statistic)
                .where(
                        statistic.orderedAt.goe(fromDate.atStartOfDay()),
                        statistic.orderedAt.lt(
                                toDate.plusDays(1).atStartOfDay()
                        )
                )
                .groupBy(orderDate)
                .orderBy(orderDate.asc())
                .fetch();
	}
}