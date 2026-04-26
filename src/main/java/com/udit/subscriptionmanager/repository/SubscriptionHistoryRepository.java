package com.udit.subscriptionmanager.repository;

import com.udit.subscriptionmanager.entity.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {
    List<SubscriptionHistory> findBySubscription_IdOrderByPaymentDateDesc(Long subscriptionId);

    @Query("SELECT sh FROM SubscriptionHistory sh JOIN FETCH sh.subscription WHERE sh.subscription.user.id = :userId ORDER BY sh.paymentDate DESC")
    List<SubscriptionHistory> findBySubscriptionUserIdOrderByPaymentDateDesc(@Param("userId") Long userId);

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
