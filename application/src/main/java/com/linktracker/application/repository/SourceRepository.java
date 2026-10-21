package com.linktracker.application.repository;

import com.linktracker.application.model.Source;
import com.linktracker.application.model.SourceType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, Long> {

    Optional<Source> findByTypeAndExternalId(SourceType type, String externalId);
}
