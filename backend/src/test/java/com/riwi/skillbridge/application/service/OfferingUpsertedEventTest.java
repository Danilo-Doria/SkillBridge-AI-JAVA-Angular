package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateOfferingUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.OfferingUpsertedPayload;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.policy.OfferingAccessPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HU-104: el monolito publica el estado completo de la oferta para read models externos (ai-service). */
class OfferingUpsertedEventTest {

    private final OfferingRepositoryPort repository = mock(OfferingRepositoryPort.class);
    private final AuditEventPublisherPort publisher = mock(AuditEventPublisherPort.class);
    private final OfferingCommandService service = new OfferingCommandService(
        repository, mock(OfferingCachePort.class), new OfferingAccessPolicy(), publisher);

    private final UUID ownerId = UUID.randomUUID();
    private final Offering offering =
        new Offering(UUID.randomUUID(), ownerId, "Java", "desc", "BACKEND", BigDecimal.TEN, true);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<BusinessEvent<?>> published() {
        ArgumentCaptor<BusinessEvent> captor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());
        return (List) captor.getAllValues();
    }

    private BusinessEvent<?> upserted() {
        return published().stream().filter(e -> "OfferingUpserted".equals(e.eventType()))
            .reduce((a, b) -> b).orElseThrow(() -> new AssertionError("sin OfferingUpserted"));
    }

    @Test
    void create_publica_audit_y_upserted_con_estado_completo() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        Offering saved = service.create(new Actor(ownerId, Role.PROVIDER),
            new CreateOfferingUseCase.Command(null, "T", "d", "C", BigDecimal.ONE));

        assertTrue(published().stream().anyMatch(e -> "OfferingCreated".equals(e.eventType())));
        BusinessEvent<?> e = upserted();
        assertEquals(saved.id().toString(), e.aggregateId(), "la clave Kafka (aggregateId) es el offeringId");
        assertEquals("Offering", e.aggregateType());
        assertEquals(1, e.version());
        OfferingUpsertedPayload p = (OfferingUpsertedPayload) e.payload();
        assertEquals(saved.id(), p.id());
        assertEquals(ownerId, p.providerId());
        assertEquals("T", p.title());
        assertEquals(0, BigDecimal.ONE.compareTo(p.price()));
        assertTrue(p.active());
        assertNotNull(p.mode());
        assertEquals(saved.durationHours(), p.durationHours());
    }

    @Test
    void deactivate_publica_upserted_con_active_false() {
        when(repository.findById(offering.id())).thenReturn(Optional.of(offering));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.deactivate(new Actor(ownerId, Role.PROVIDER), offering.id());

        assertFalse(((OfferingUpsertedPayload) upserted().payload()).active());
    }

    @Test
    void deactivate_idempotente_no_publica_nada() {
        Offering inactive = offering.deactivate();
        when(repository.findById(inactive.id())).thenReturn(Optional.of(inactive));

        service.deactivate(new Actor(ownerId, Role.PROVIDER), inactive.id());

        verifyNoInteractions(publisher);
    }

    @Test
    void snapshot_publica_todas_las_ofertas_incluidas_inactivas_sin_actor() {
        Offering other = new Offering(UUID.randomUUID(), ownerId, "Go", "d", "BACKEND", BigDecimal.ONE, false);
        when(repository.findAll()).thenReturn(List.of(offering, other));

        assertEquals(2, service.publishActiveSnapshot());

        List<BusinessEvent<?>> events = published();
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> "OfferingUpserted".equals(e.eventType()) && e.actorUserId() == null));
        assertTrue(events.stream().anyMatch(e -> !((OfferingUpsertedPayload) e.payload()).active()),
            "una oferta inactiva también llega al read model");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void con_cableado_completo_upserted_va_por_booking_events_y_la_auditoria_por_su_puerto() {
        var catalog = mock(com.riwi.skillbridge.application.port.out.BookingEventPublisherPort.class);
        var audit = mock(AuditEventPublisherPort.class);
        var wired = new OfferingCommandService(repository, mock(OfferingCachePort.class), new OfferingAccessPolicy(),
            audit, null, catalog);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        wired.create(new Actor(ownerId, Role.PROVIDER), new CreateOfferingUseCase.Command(null, "T", "d", "C", BigDecimal.ONE));

        ArgumentCaptor<BusinessEvent> toCatalog = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(catalog).publish(toCatalog.capture());
        assertEquals("OfferingUpserted", toCatalog.getValue().eventType());
        ArgumentCaptor<BusinessEvent> toAudit = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(audit).publish(toAudit.capture());
        assertEquals("OfferingCreated", toAudit.getValue().eventType());
    }

    @Test
    void payload_serializado_lleva_los_campos_del_contrato() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.create(new Actor(ownerId, Role.PROVIDER),
            new CreateOfferingUseCase.Command(null, "T", "d", "C", BigDecimal.ONE));

        var map = upserted().getPayloadForAudit();
        for (String k : List.of("id", "providerId", "title", "description", "category", "price", "active", "mode", "durationHours")) {
            assertTrue(map.containsKey(k), "falta " + k + " en " + map.keySet());
        }
    }
}
