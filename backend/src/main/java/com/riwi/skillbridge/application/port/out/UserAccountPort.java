package com.riwi.skillbridge.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountPort {
    Optional<UUID> findIdByEmail(String email);
}
