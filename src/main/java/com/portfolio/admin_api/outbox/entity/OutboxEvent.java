package com.portfolio.admin_api.outbox.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "aggregate_id", nullable = false)
	private Long aggregateId;

	@Column(name = "payload", nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OutboxStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at", nullable = false)
	private LocalDateTime nextAttemptAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "claim_token", length = 36)
	private String claimToken;

	@Column(name = "locked_until")
	private LocalDateTime lockedUntil;

	public static OutboxEvent create(String eventType, Long aggregateId, String payload, LocalDateTime now) {

		if (eventType == null || eventType.isBlank() || eventType.length() > 100) {
			throw new IllegalArgumentException("이벤트 유형은 1~100자의 문자열이어야 합니다.");
		}

		if (aggregateId == null || aggregateId <= 0) {
			throw new IllegalArgumentException("이벤트 대상 ID는 양수여야 합니다.");
		}

		if (payload == null || payload.isBlank()) {
			throw new IllegalArgumentException("이벤트 데이터는 필수입니다.");
		}

		if (now == null) {
			throw new IllegalArgumentException("Outbox 생성 시각은 필수입니다.");
		}

		OutboxEvent event = new OutboxEvent();
		event.eventType = eventType;
		event.aggregateId = aggregateId;
		event.payload = payload;
		event.status = OutboxStatus.PENDING;
		event.attemptCount = 0;
		event.nextAttemptAt = now;
		event.createdAt = now;

		return event;
	}

	public void claim(String claimToken, LocalDateTime lockedUntil) {
		if (this.status != OutboxStatus.PENDING) {
			throw new IllegalStateException("전송 대기 상태의 이벤트만 선점할 수 있습니다.");
		}

		if (claimToken == null || claimToken.isBlank() || claimToken.length() > 36) {
			throw new IllegalArgumentException("선점 토큰은 1~36자의 문자열이어야 합니다.");
		}

		if (lockedUntil == null) {
			throw new IllegalArgumentException("처리 권한 만료 시각은 필수입니다.");
		}

		this.status = OutboxStatus.PROCESSING;
		this.claimToken = claimToken;
		this.lockedUntil = lockedUntil;
	}

}
