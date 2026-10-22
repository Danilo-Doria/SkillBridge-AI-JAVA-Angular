package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import com.riwi.skillbridge.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAuthorizationServiceTest {

    @Mock
    private BookingRepositoryPort bookingRepositoryPort;

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
    private UUID ownerId;
    private Actor owner;

    @BeforeEach
    void setUp() {
        idempotencyKey = "test-key-123";
        bookingId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        owner = new Actor(ownerId, "owner@test.com", Role.CUSTOMER);
        lenient().when(bookingRepositoryPort.findById(bookingId)).thenReturn(Optional.of(bookingOf(ownerId)));
        command = new PaymentCommand(idempotencyKey, bookingId, new BigDecimal("50.00"), "1111", "12/25", "123");
    }

    @Test
    void testIdempotencyRepeat() {
        // Prueba de Idempotencia: Si la llave ya existe, retorna el resultado previo sin volver a procesar
        PaymentResult existingResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.APPROVED, "Already approved");
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingResult));

        PaymentResult result = service.authorize(command, owner);

        assertEquals(existingResult, result);
        verify(paymentPort, never()).process(any()); // Verifica que NO se llamo a la pasarela
        verify(eventPublisherPort, never()).publishApproved(any()); // Verifica que NO se emitio otro evento
    }

    @Test
    void testPaymentApproved() {
        // Prueba flujo feliz: Pago aprobado, guarda el resultado y emite el evento correspondiente
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        PaymentResult newResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.APPROVED, "Payment approved");
        when(paymentPort.process(command)).thenReturn(newResult);

        PaymentResult result = service.authorize(command, owner);

        assertEquals(newResult, result);
        verify(repositoryPort).save(idempotencyKey, newResult); // Verifica guardado
        verify(eventPublisherPort).publishApproved(any(PaymentApproved.class)); // Verifica evento emitido
    }

    @Test
    void testPaymentDeclined() {
        // Prueba flujo de rechazo: Pago rechazado, guarda el resultado y emite el evento correspondiente
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        PaymentResult newResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.DECLINED, "Card declined");
        when(paymentPort.process(command)).thenReturn(newResult);

        PaymentResult result = service.authorize(command, owner);

        assertEquals(newResult, result);
        verify(repositoryPort).save(idempotencyKey, newResult); // Verifica guardado
        verify(eventPublisherPort).publishRejected(any(PaymentRejected.class)); // Verifica evento emitido
    }

    @Test
    void otro_actor_recibe_forbidden_antes_de_consultar_idempotencia_o_pasarela() {
        Actor intruder = new Actor(UUID.randomUUID(), "x@test.com", Role.CUSTOMER);

        assertThrows(ForbiddenOperationException.class, () -> service.authorize(command, intruder));

        verifyNoInteractions(repositoryPort, paymentPort, eventPublisherPort);
    }

    @Test
    void admin_y_provider_tampoco_pagan_por_terceros() {
        for (Role role : new Role[]{Role.ADMIN, Role.PROVIDER}) {
            Actor other = new Actor(UUID.randomUUID(), "x@test.com", role);
            assertThrows(ForbiddenOperationException.class, () -> service.authorize(command, other));
        }
        verifyNoInteractions(repositoryPort, paymentPort, eventPublisherPort);
    }

    @Test
    void reserva_inexistente_lanza_not_found_sin_tocar_idempotencia_ni_pasarela() {
        when(bookingRepositoryPort.findById(bookingId)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.authorize(command, owner));

        verifyNoInteractions(repositoryPort, paymentPort, eventPublisherPort);
    }

    @Test
    void booking_id_nulo_se_rechaza() {
        PaymentCommand sinReserva = new PaymentCommand(idempotencyKey, null, new BigDecimal("50.00"), "1111", "12/25", "123");

        assertThrows(IllegalArgumentException.class, () -> service.authorize(sinReserva, owner));

        verifyNoInteractions(repositoryPort, paymentPort, eventPublisherPort);
    }

    @Test
    void clave_de_idempotencia_asociada_a_otra_reserva_no_revela_el_resultado_guardado() {
        PaymentResult ajeno = new PaymentResult(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.APPROVED, "de otra reserva");
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(ajeno));

        assertThrows(ForbiddenOperationException.class, () -> service.authorize(command, owner));

        verify(paymentPort, never()).process(any());
        verify(eventPublisherPort, never()).publishApproved(any());
    }

    private Booking bookingOf(UUID customerId) {
        return new Booking(bookingId, UUID.randomUUID(), customerId,
                Instant.parse("2030-01-01T12:00:00Z"), BookingStatus.CREATED, 0);
    }
}
