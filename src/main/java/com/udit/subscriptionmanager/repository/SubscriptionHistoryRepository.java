package com.udit.subscriptionmanager.repository;

import com.udit.subscriptionmanager.entity.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {
    List<SubscriptionHistory> findBySubscriptionIdOrderByPaymentDateDesc(Long subscriptionId);
}
