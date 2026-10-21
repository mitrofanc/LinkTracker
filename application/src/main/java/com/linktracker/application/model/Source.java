package com.linktracker.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(name = "sources")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SourceType type;

    @Column(name = "canonical_url", nullable = false, unique = true, columnDefinition = "text")
    private String canonicalUrl;

    @Column(name = "external_id", nullable = false, columnDefinition = "text")
    private String externalId;

    protected Source() {
    }

    public Source(SourceType type, String canonicalUrl, String externalId) {
        this.type = Objects.requireNonNull(type);
        this.canonicalUrl = Objects.requireNonNull(canonicalUrl);
        this.externalId = Objects.requireNonNull(externalId);
    }
}
