package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAdminPort {
    Optional<UserAccount> findById(UUID id);
    PageResult<UserAccount> findAll(PageQuery query);
    UserAccount save(UserAccount user);

    /** HU-104: usuarios con un estado dado (snapshot de revocaciones para servicios extraídos). */
    java.util.List<UserAccount> findByStatus(com.riwi.skillbridge.domain.model.UserStatus status);
}
