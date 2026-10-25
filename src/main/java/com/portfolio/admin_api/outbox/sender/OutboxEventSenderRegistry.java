package com.portfolio.admin_api.outbox.sender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class OutboxEventSenderRegistry {

	private final Map<String, OutboxEventSender> senders;

	public OutboxEventSenderRegistry(List<OutboxEventSender> senderList) {
		Map<String, OutboxEventSender> senderMap = new HashMap<>();

		for (OutboxEventSender sender : senderList) {
			String eventType = sender.supportedEventType();

			if (eventType == null || eventType.isBlank()) {
				throw new IllegalStateException("Sender의 이벤트 유형은 필수입니다.");
			}

			OutboxEventSender previous = senderMap.putIfAbsent(eventType, sender);

			if (previous != null) {
				throw new IllegalStateException("중복된 이벤트 Sender가 있습니다: " + eventType);
			}
		}

		this.senders = Map.copyOf(senderMap);
	}

	public OutboxEventSender getSender(String eventType) {
		OutboxEventSender sender = senders.get(eventType);

		if (sender == null) {
			throw new IllegalArgumentException("지원하는 Sender가 없는 이벤트 유형입니다: " + eventType);
		}

		return sender;
	}

}
