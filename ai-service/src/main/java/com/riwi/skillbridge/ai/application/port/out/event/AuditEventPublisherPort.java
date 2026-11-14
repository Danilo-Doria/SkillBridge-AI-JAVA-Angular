package com.riwi.skillbridge.ai.application.port.out.event;

public interface AuditEventPublisherPort {
    void publish(BusinessEvent<?> event);
}
