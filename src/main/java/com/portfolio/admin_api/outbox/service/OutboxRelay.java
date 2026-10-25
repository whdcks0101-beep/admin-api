package com.portfolio.admin_api.outbox.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.portfolio.admin_api.outbox.config.OutboxProperties;
import com.portfolio.admin_api.outbox.entity.OutboxEvent;
import com.portfolio.admin_api.outbox.sender.OutboxEventSender;
import com.portfolio.admin_api.outbox.sender.OutboxEventSenderRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

	private final OutboxClaimService outboxClaimService;
	private final OutboxEventSenderRegistry senderRegistry;
	private final OutboxStatusService outboxStatusService;
	private final OutboxProperties outboxProperties;
	@Value("${server.port:8080}")
	private int serverPort;

	public void publishPending() {
		for (int i = 0; i < outboxProperties.getMaxEventsPerRun(); i++) {
			if (Thread.currentThread().isInterrupted()) {
				return;
			}

			Optional<OutboxEvent> claimedEvent = outboxClaimService.claimNext();

			if (claimedEvent.isEmpty()) {
				return;
			}

			publishOne(claimedEvent.get());
		}
	}

	private void publishOne(OutboxEvent event) {
		try {
			OutboxEventSender sender = senderRegistry.getSender(event.getEventType());
			sender.send(event);
		} catch (RuntimeException e) {
			log.error(
			        "Outbox 작업 실행 실패: eventType={}, outboxId={}",
			        event.getEventType(),
			        event.getId(),
			        e
			);

			if (Thread.currentThread().isInterrupted()) {
				return;
			}

			try {
				boolean rescheduled = outboxStatusService.recordFailure(event.getId(), event.getClaimToken());

				if (rescheduled) {
					log.info("Outbox 재시도 예약 완료: outboxId={}", event.getId());
				} else {
					log.warn("Outbox 재시도 예약 조건 불일치: outboxId={}", event.getId());
				}
			} catch (RuntimeException recordingException) {
				log.error("Outbox 실패 기록 저장 실패: outboxId={}", event.getId(), recordingException);
			}

			return;
		}

		try {
			boolean published = outboxStatusService.markPublished(event.getId(), event.getClaimToken());
			if (published) {
				log.info(
				        "Outbox 작업 및 완료 처리 성공: port={}, eventType={}, outboxId={}, aggregateId={}",
				        serverPort,
				        event.getEventType(),
				        event.getId(),
				        event.getAggregateId()
				);
			} else {
				log.warn(
				        "Outbox 작업 성공 후 완료 처리 조건 불일치: eventType={}, outboxId={}",
				        event.getEventType(),
				        event.getId()
				);
			}
		} catch (RuntimeException e) {
			log.error(
			        "Outbox 작업 성공 후 완료 상태 저장 실패: eventType={}, outboxId={}",
			        event.getEventType(),
			        event.getId(),
			        e
			);
		}
	}
}