package com.riwi.skillbridge.payment.domain.exception;

/**
 * 409: el read model local todavía no conoce la reserva u oferta (consistencia eventual con {@code booking-events}).
 * El pago nunca se acepta en este caso; el cliente puede reintentar en unos segundos.
 */
public class ReadModelNotSynchronizedException extends RuntimeException {
    public static final String BOOKING = "BOOKING_NOT_SYNCHRONIZED";
    public static final String OFFERING = "OFFERING_NOT_SYNCHRONIZED";

    private final String code;

    public ReadModelNotSynchronizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
