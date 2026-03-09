package com.udit.subscriptionmanager.repository;

import com.udit.subscriptionmanager.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserId(Long userId);
    List<Subscription> findByHouseholdId(Long householdId);
}