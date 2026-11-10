package com.nexaai.chat.repository;

import com.nexaai.chat.domain.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, UUID> {
    List<WorkflowExecution> findByWorkflowIdAndUserIdOrderByStartedAtDesc(UUID workflowId, UUID userId);
    List<WorkflowExecution> findByUserIdOrderByStartedAtDesc(UUID userId);
    Optional<WorkflowExecution> findByIdAndUserId(UUID id, UUID userId);
}
