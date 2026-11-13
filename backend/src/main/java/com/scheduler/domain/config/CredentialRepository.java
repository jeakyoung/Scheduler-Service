package com.scheduler.domain.config;

import java.util.List;
import java.util.Optional;

public interface CredentialRepository {

    List<Credential> findAll();

    Optional<Credential> findById(long id);

    Optional<Credential> findByName(String name);

    Credential save(Credential credential);

    Credential update(Credential credential);

    void deleteById(long id);
}
