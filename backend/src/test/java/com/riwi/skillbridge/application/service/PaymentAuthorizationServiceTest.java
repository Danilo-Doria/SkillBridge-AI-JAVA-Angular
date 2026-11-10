package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - Servicio de Pagos")
class PaymentAuthorizationServiceTest {

    @Mock
    private PaymentPort paymentPort;

    @Mock
    private PaymentResultRepositoryPort repositoryPort;

    @Mock
    private PaymentEventPublisherPort eventPublisherPort;

    @InjectMocks
    private PaymentAuthorizationService service;

    private PaymentCommand command;
    private UUID bookingId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        idempotencyKey = "test-key-123";
        bookingId = UUID.randomUUID();
        command = new PaymentCommand(idempotencyKey, bookingId, new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Caso Borde: Si dan clic dos veces, devuelve el cobro previo sin duplicar")
    void testIdempotencyRepeat() {
        // [Caso Borde]: Evita cobros dobles si el usuario repite la petición; no vuelve a llamar a Stripe.
        PaymentResult existingResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.APPROVED, "Already approved", "pi_test_secret");
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingResult));

        PaymentResult result = service.authorize(command);

        assertEquals(existingResult, result);
        verify(paymentPort, never()).process(any());
        verify(eventPublisherPort, never()).publishApproved(any());
    }

    @Test
    @DisplayName("Caso Feliz: Pago aprobado, guarda en BD y emite PaymentApproved")
    void testPaymentApproved() {
        // [Caso Feliz]: La tarjeta pasa con éxito, guardamos el resultado y confirmamos la reserva.
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        PaymentResult newResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.APPROVED, "Payment approved", "pi_test_secret");
        when(paymentPort.process(command)).thenReturn(newResult);

        PaymentResult result = service.authorize(command);

        assertEquals(newResult, result);
        verify(repositoryPort).save(idempotencyKey, newResult);
        verify(eventPublisherPort).publishApproved(any(PaymentApproved.class));
    }

    @Test
    @DisplayName("Caso Triste: Tarjeta rechazada, guarda en BD y emite PaymentRejected")
    void testPaymentDeclined() {
        // [Caso Triste]: El banco rechaza el pago, guardamos el fallo y avisamos para no confirmar la cita.
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        PaymentResult newResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.DECLINED, "Card declined", null);
        when(paymentPort.process(command)).thenReturn(newResult);

        PaymentResult result = service.authorize(command);

        assertEquals(newResult, result);
        verify(repositoryPort).save(idempotencyKey, newResult);
        verify(eventPublisherPort).publishRejected(any(PaymentRejected.class));
    }
}
