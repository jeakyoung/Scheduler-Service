package com.scheduler.domain.config;

import java.util.List;
import java.util.Optional;

public interface TargetDbConfigRepository {

    List<TargetDbConfig> findAll();

    Optional<TargetDbConfig> findById(long id);

    TargetDbConfig save(TargetDbConfig config);

    TargetDbConfig update(TargetDbConfig config);

    void deleteById(long id);
}
