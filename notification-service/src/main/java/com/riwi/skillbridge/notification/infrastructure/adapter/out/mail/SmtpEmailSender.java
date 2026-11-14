package com.riwi.skillbridge.notification.infrastructure.adapter.out.mail;

import com.riwi.skillbridge.notification.application.port.out.EmailSenderPort;
import com.riwi.skillbridge.notification.domain.exception.PermanentNotificationException;
import com.riwi.skillbridge.notification.domain.model.EmailContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envío SMTP configurado por {@code spring.mail.*} (Mailpit {@code mailpit:1025} en local, SMTP real por
 * variables). Un {@code MailSendException} (servidor caído, rechazo temporal) se propaga para que actúen
 * reintento y DLT; una dirección mal formada es permanente.
 */
@Component
public class SmtpEmailSender implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${notification.email.from:no-reply@skillbridge.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailContent email) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(email.to());
        mail.setSubject(email.subject());
        mail.setText(email.body());
        try {
            mailSender.send(mail);
        } catch (MailParseException e) {
            throw new PermanentNotificationException("Correo mal formado: " + e.getMessage());
        }
        log.info("Email sent subject=\"{}\"", email.subject());
    }
}
