package com.riwi.skillbridge.notification.application.port.out;

import com.riwi.skillbridge.notification.domain.model.EmailContent;

/** Envío de correo. Los fallos transitorios se propagan para que actúen reintento y DLT. */
public interface EmailSenderPort {
    void send(EmailContent email);
}
