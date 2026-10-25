package com.portfolio.admin_api.order.statistics.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_statistics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatistic {

	@Id
	@Column(name = "order_id")
	private Long orderId;

	@Column(name = "ordered_at", nullable = false)
	private LocalDateTime orderedAt;

	public static OrderStatistic create(Long orderId, LocalDateTime orderedAt) {

		if (orderId == null || orderId <= 0) {
			throw new IllegalArgumentException("주문 번호는 양수여야 합니다.");
		}

		if (orderedAt == null) {
			throw new IllegalArgumentException("주문 생성 시각은 필수입니다.");
		}

		OrderStatistic statistic = new OrderStatistic();
		statistic.orderId = orderId;
		statistic.orderedAt = orderedAt;

		return statistic;
	}

}
