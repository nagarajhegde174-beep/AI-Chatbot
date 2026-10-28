package com.nexaai.analytics.repository;

import com.nexaai.analytics.domain.UserActivityReadModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserActivityReadModelRepository extends JpaRepository<UserActivityReadModel, UUID> {

    @Query("SELECT COUNT(u) FROM UserActivityReadModel u WHERE u.lastLoginAt >= :since")
    long countActiveUsersSince(@Param("since") Instant since);

    long countBySubscriptionTier(String subscriptionTier);

    List<UserActivityReadModel> findByEmailContainingIgnoreCase(String emailQuery);

    List<UserActivityReadModel> findByStatus(String status);
}
