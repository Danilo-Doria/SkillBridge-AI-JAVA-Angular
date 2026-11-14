package com.riwi.skillbridge.application.port.out.event;

import java.util.UUID;

/**
 * HU-104 (hallazgo S1): estado de acceso de un usuario para servicios que validan el JWT sin consultar users
 * (ai-service, payment-service). Solo ids y valores de estado/rol: sin PII. Se publica como
 * {@code UserAccessChanged} v1 en {@code booking-events} con clave {@code userId}.
 */
public record UserAccessChangedPayload(UUID userId, String status, String role) { }
