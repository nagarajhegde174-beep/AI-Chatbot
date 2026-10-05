package com.nexaai.user.repository;

import com.nexaai.user.domain.UserStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the append-only {@code user_status_history} table. */
public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, UUID> {

    List<UserStatusHistory> findByUserProfileIdOrderByChangedAtDesc(UUID userProfileId);

    List<UserStatusHistory> findTop100ByOrderByChangedAtDesc();
}