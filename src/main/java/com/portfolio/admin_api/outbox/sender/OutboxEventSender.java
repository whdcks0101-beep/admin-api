package com.portfolio.admin_api.outbox.sender;

import com.portfolio.admin_api.outbox.entity.OutboxEvent;

public interface OutboxEventSender {

    String supportedEventType();

    void send(OutboxEvent event);
    
}
