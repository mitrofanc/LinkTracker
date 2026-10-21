package com.linktracker.application.repository;

import com.linktracker.application.model.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @EntityGraph(attributePaths = "source")
    List<Subscription> findAllByUserIdOrderByIdAsc(Long userId);

    @EntityGraph(attributePaths = "source")
    Optional<Subscription> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndSourceId(Long userId, Long sourceId);
}
