package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.UserAccount;
import java.util.Optional;

public interface UserRepositoryPort {
    boolean existsByEmail(String email);
    Optional<UserAccount> findByEmail(String email);
    UserAccount save(UserAccount user);
}
