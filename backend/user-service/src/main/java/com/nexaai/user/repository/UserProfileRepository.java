package com.nexaai.user.repository;

import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.Role;
import com.nexaai.user.domain.UserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@code user_profile}.
 *
 * <p>Every lookup is by {@code auth_user_id}, by the internal id, or by normalised email.
 * There is deliberately no finder that returns a list where the caller expects one.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByAuthUserId(UUID authUserId);

    Optional<UserProfile> findByEmail(String email);

    boolean existsByAuthUserId(UUID authUserId);

    long countByAccountStatus(AccountStatus status);

    /**
     * The admin listing: filter, search and page.
     *
     * <p>A single query rather than a filter-then-paginate in memory. Loading every user to
     * search them is what turns an admin page into an outage once the table is large.
     *
     * <p>Soft-deleted rows are excluded here and in every other read, so a deleted account
     * cannot reappear in a list.
     */
    @Query("""
            select p from UserProfile p
            where p.deletedAt is null
              and (:status is null or p.accountStatus = :status)
              and (:role is null or p.role = :role)
              and (:search is null
                   or lower(p.displayName) like :search
                   or lower(p.email) like :search)
            """)
    Page<UserProfile> search(@Param("status") AccountStatus status,
                             @Param("role") Role role,
                             @Param("search") String search,
                             Pageable pageable);

    /** Liveness count for the admin dashboard. Excludes soft-deleted rows. */
    @Query("select count(p) from UserProfile p where p.deletedAt is null")
    long countNotDeleted();
}