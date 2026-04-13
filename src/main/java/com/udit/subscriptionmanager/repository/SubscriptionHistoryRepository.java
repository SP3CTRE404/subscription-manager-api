package com.udit.subscriptionmanager.repository;

import com.udit.subscriptionmanager.entity.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {
    List<SubscriptionHistory> findBySubscriptionIdOrderByPaymentDateDesc(Long subscriptionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SubscriptionHistory sh WHERE sh.subscription.id IN " +
           "(SELECT s.id FROM Subscription s WHERE s.user.id = :userId)")
    void deleteBySubscriptionUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SubscriptionHistory sh WHERE sh.subscription.id IN " +
           "(SELECT s.id FROM Subscription s WHERE s.household.id = :householdId)")
    void deleteBySubscriptionHouseholdId(@Param("householdId") Long householdId);
}
