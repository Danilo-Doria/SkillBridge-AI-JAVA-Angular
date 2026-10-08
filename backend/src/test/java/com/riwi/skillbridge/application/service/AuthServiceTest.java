package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepositoryPort users;
    private PasswordHasherPort passwords;
    private TokenPort tokens;
    private AuditEventPublisherPort auditPublisher;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(UserRepositoryPort.class);
        passwords = mock(PasswordHasherPort.class);
        tokens = mock(TokenPort.class);
        auditPublisher = mock(AuditEventPublisherPort.class);
        authService = new AuthService(users, passwords, tokens, auditPublisher);
    }

    @Test
    void testRegisterEmitsEvent() {
        UserAccount savedUser = new UserAccount(UUID.randomUUID(), "Test User", "test@example.com", "hash", Role.CUSTOMER);
        when(users.existsByEmail("test@example.com")).thenReturn(false);
        when(users.save(any(UserAccount.class))).thenReturn(savedUser);
        when(passwords.encode("password")).thenReturn("hash");
        when(tokens.generate("test@example.com", "CUSTOMER")).thenReturn("token");

        authService.register("Test User", "test@example.com", "password");

        ArgumentCaptor<BusinessEvent> captor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(auditPublisher).publish(captor.capture());

        BusinessEvent event = captor.getValue();
        assertEquals("UserRegistered", event.eventType());
        assertEquals("REGISTER", event.action());
        assertEquals("USER", event.resource());
        assertEquals(savedUser.id().toString(), event.actorUserId());
        assertEquals("test@example.com", event.actorUsername());
        assertEquals("CUSTOMER", event.actorRole());
    }

    @Test
    void testLoginEmitsEvent() {
        UserAccount user = new UserAccount(UUID.randomUUID(), "Test User", "test@example.com", "hash", Role.CUSTOMER);
        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("password", "hash")).thenReturn(true);
        when(tokens.generate("test@example.com", "CUSTOMER")).thenReturn("token");

        authService.login("test@example.com", "password");

        ArgumentCaptor<BusinessEvent> captor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(auditPublisher).publish(captor.capture());

        BusinessEvent event = captor.getValue();
        assertEquals("UserLoggedIn", event.eventType());
        assertEquals("LOGIN", event.action());
        assertEquals("USER", event.resource());
        assertEquals(user.id().toString(), event.actorUserId());
        assertEquals("test@example.com", event.actorUsername());
        assertEquals("CUSTOMER", event.actorRole());
    }
}
