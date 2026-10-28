package com.nexaai.analytics.repository;

import com.nexaai.analytics.domain.ModelRegistryState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelRegistryStateRepository extends JpaRepository<ModelRegistryState, String> {

    Optional<ModelRegistryState> findByIsDefaultTrue();
}
