package com.linktracker.application.repository;

import com.linktracker.application.model.Source;
import com.linktracker.application.model.SourceType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceRepository extends JpaRepository<Source, Long> {

    Optional<Source> findByTypeAndExternalId(SourceType type, String externalId);

    // Both canonical_url and (type, external_id) are unique; concurrent inserts
    // must handle either conflict without aborting the transaction.
    @Modifying
    @Query(value = """
            INSERT INTO sources (type, canonical_url, external_id)
            VALUES (:type, :canonicalUrl, :externalId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("type") String type, @Param("canonicalUrl") String canonicalUrl,
            @Param("externalId") String externalId);
}
