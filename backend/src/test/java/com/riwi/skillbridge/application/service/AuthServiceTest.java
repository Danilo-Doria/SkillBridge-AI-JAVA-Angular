package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final PasswordHasherPort passwords = mock(PasswordHasherPort.class);
    private final TokenPort tokens = mock(TokenPort.class);
    private final AuthService service = new AuthService(users, passwords, tokens);

    private UserAccount account(UserStatus status) {
        return new UserAccount(UUID.randomUUID(), "Ana", "ana@test.com", "hash", Role.CUSTOMER, status);
    }

    @Test
    void login_de_cuenta_activa_devuelve_token() {
        when(users.findByEmail("ana@test.com")).thenReturn(Optional.of(account(UserStatus.ACTIVE)));
        when(passwords.matches("clave", "hash")).thenReturn(true);
        when(tokens.generate("ana@test.com", "CUSTOMER")).thenReturn("jwt");

        assertEquals("jwt", service.login("ana@test.com", "clave"));
    }

    @Test
    void login_de_cuenta_suspendida_es_rechazado_y_no_emite_token() {
        when(users.findByEmail("ana@test.com")).thenReturn(Optional.of(account(UserStatus.SUSPENDED)));
        when(passwords.matches("clave", "hash")).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> service.login("ana@test.com", "clave"));
        verify(tokens, never()).generate(any(), any());
    }

    @Test
    void cuenta_suspendida_con_clave_incorrecta_no_revela_su_estado() {
        when(users.findByEmail("ana@test.com")).thenReturn(Optional.of(account(UserStatus.SUSPENDED)));
        when(passwords.matches("mala", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> service.login("ana@test.com", "mala"));
    }
}
