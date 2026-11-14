package com.riwi.skillbridge.notification.application.port.in;

import com.riwi.skillbridge.notification.domain.model.NotificationRequest;

public interface ProcessNotificationUseCase {
    void process(NotificationRequest request);
}
