package com.nexaai.subscription.repo;

import com.nexaai.subscription.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findTopByUserIdOrderByStartAtDesc(String userId);
    List<Subscription> findAllByUserIdOrderByStartAtDesc(String userId);
}
