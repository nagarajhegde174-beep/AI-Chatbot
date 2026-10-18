package com.nexaai.user.domain.repository;

import com.nexaai.user.domain.entity.UserProfile;
import com.nexaai.user.domain.model.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM UserProfile u WHERE " +
            "(:query IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "  OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND (:status IS NULL OR u.accountStatus = :status)")
    Page<UserProfile> searchUsers(
            @Param("query") String query,
            @Param("status") AccountStatus status,
            Pageable pageable
    );
}
