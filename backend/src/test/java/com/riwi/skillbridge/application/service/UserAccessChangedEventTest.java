package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.UserAccessChangedPayload;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import com.riwi.skillbridge.domain.policy.UserManagementPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HU-104 (S1): cambios de acceso llegan a los servicios extraídos como UserAccessChanged (sin PII). */
class UserAccessChangedEventTest {

    private final UserAdminPort users = mock(UserAdminPort.class);
    private final BookingEventPublisherPort events = mock(BookingEventPublisherPort.class);
    private final UserAdminService service = new UserAdminService(users, new UserManagementPolicy(), events);
    private final Actor admin = new Actor(UUID.randomUUID(), Role.ADMIN);
    private final UserAccount customer =
        new UserAccount(UUID.randomUUID(), "Ana", "ana@test.com", "hash", Role.CUSTOMER);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BusinessEvent<?> published() {
        ArgumentCaptor<BusinessEvent> c = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(events).publish(c.capture());
        return c.getValue();
    }

    @Test
    void suspender_publica_user_access_changed_sin_pii() {
        when(users.findById(customer.id())).thenReturn(Optional.of(customer));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));

        service.changeStatus(admin, customer.id(), UserStatus.SUSPENDED);

        BusinessEvent<?> e = published();
        assertEquals("UserAccessChanged", e.eventType());
        assertEquals(customer.id().toString(), e.aggregateId(), "clave Kafka = userId");
        UserAccessChangedPayload p = (UserAccessChangedPayload) e.payload();
        assertEquals("SUSPENDED", p.status());
        assertEquals("CUSTOMER", p.role());
        assertNull(e.actorUsername(), "sin email del actor en el evento");
        assertFalse(e.getPayloadForAudit().toString().contains("ana@test.com"), "sin PII del usuario");
    }

    @Test
    void cambiar_rol_publica_el_rol_nuevo() {
        when(users.findById(customer.id())).thenReturn(Optional.of(customer));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));

        service.changeRole(admin, customer.id(), Role.PROVIDER);

        assertEquals("PROVIDER", ((UserAccessChangedPayload) published().payload()).role());
    }

    @Test
    void snapshot_publica_un_evento_por_usuario_suspendido() {
        UserAccount suspended = customer.withStatus(UserStatus.SUSPENDED);
        when(users.findByStatus(UserStatus.SUSPENDED)).thenReturn(List.of(suspended));

        assertEquals(1, service.publishRestrictedUsersSnapshot());
        assertEquals("SUSPENDED", ((UserAccessChangedPayload) published().payload()).status());
    }

    @Test
    void rechazo_de_politica_no_publica() {
        when(users.findById(customer.id())).thenReturn(Optional.of(customer));
        Actor notAdmin = new Actor(UUID.randomUUID(), Role.CUSTOMER);

        assertThrows(RuntimeException.class, () -> service.changeStatus(notAdmin, customer.id(), UserStatus.SUSPENDED));
        verifyNoInteractions(events);
    }
}
