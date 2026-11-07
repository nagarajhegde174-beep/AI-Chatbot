package com.nexaai.analytics.repository;

import com.nexaai.analytics.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:filter IS NULL OR LOWER(a.actor) LIKE LOWER(CONCAT('%', :filter, '%')) OR LOWER(a.action) LIKE LOWER(CONCAT('%', :filter, '%'))) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findFiltered(@Param("filter") String filter, Pageable pageable);
}
