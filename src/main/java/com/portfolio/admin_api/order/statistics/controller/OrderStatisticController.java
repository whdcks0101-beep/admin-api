package com.portfolio.admin_api.order.statistics.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.admin_api.order.statistics.dto.DailyOrderStatisticResponse;
import com.portfolio.admin_api.order.statistics.service.OrderStatisticService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "주문 통계", description = "이벤트로 수집한 주문 통계 조회 API")
@RestController
@RequestMapping("/api/order-statistics")
@RequiredArgsConstructor
public class OrderStatisticController {

	private final OrderStatisticService orderStatisticService;

	@Operation(summary = "날짜별 주문 건수 조회", description = "시작일부터 종료일까지 조회합니다. 주문 기록이 없는 날짜는 제외됩니다.")
	@GetMapping("/daily")
	public List<DailyOrderStatisticResponse> getDailyStatistics(
			@RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

			@RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

		return orderStatisticService.getDailyStatistics(fromDate, toDate);
	}
}