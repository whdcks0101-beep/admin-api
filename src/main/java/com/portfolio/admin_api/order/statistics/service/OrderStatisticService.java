package com.portfolio.admin_api.order.statistics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.order.statistics.dto.DailyOrderStatisticResponse;
import com.portfolio.admin_api.order.statistics.entity.OrderStatistic;
import com.portfolio.admin_api.order.statistics.repository.OrderStatisticQueryRepository;
import com.portfolio.admin_api.order.statistics.repository.OrderStatisticRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderStatisticService {

	private final OrderStatisticRepository orderStatisticRepository;
	private final OrderStatisticQueryRepository orderStatisticQueryRepository;

	@Transactional
	public void recordOrder(Long orderId, LocalDateTime orderedAt) {
		OrderStatistic statistic = OrderStatistic.create(orderId, orderedAt);

		orderStatisticRepository.insertIfAbsent(statistic.getOrderId(), statistic.getOrderedAt());
	}

	public List<DailyOrderStatisticResponse> getDailyStatistics(LocalDate fromDate, LocalDate toDate) {

		if (fromDate == null || toDate == null) {
			throw new IllegalArgumentException("조회 시작일과 종료일은 필수입니다.");
		}

		if (fromDate.isAfter(toDate)) {
			throw new IllegalArgumentException("조회 시작일은 종료일보다 늦을 수 없습니다.");
		}

		if (toDate.equals(LocalDate.MAX)) {
			throw new IllegalArgumentException("조회 종료일이 허용 범위를 벗어났습니다.");
		}

		return orderStatisticQueryRepository.findDailyCounts(fromDate, toDate);
	}

}
