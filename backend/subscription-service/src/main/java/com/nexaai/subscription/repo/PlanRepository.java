package com.nexaai.subscription.repo;

import com.nexaai.subscription.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, String> {
    Optional<Plan> findByCode(String code);
}
