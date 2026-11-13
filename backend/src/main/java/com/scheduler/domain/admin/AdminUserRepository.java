package com.scheduler.domain.admin;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository {

    List<AdminUser> findAll();

    Optional<AdminUser> findByUsername(String username);

    AdminUser save(AdminUser adminUser);

    boolean existsAny();
}
