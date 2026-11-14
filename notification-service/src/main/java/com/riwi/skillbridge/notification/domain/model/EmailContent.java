package com.riwi.skillbridge.notification.domain.model;

/** Correo de texto plano listo para enviar. */
public record EmailContent(String to, String subject, String body) {
}
