package com.udit.subscriptionmanager.repository;

import com.udit.subscriptionmanager.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserId(Long userId);
    List<Subscription> findByHouseholdId(Long householdId);
    List<Subscription> findByUserIdOrHouseholdId(Long userId, Long householdId);

    @Query("SELECT s FROM Subscription s WHERE s.user.household.id = :householdId")
    List<Subscription> findByMemberHouseholdId(@Param("householdId") Long householdId);

    List<Subscription> findByNextBillingDateBefore(LocalDate date);
    List<Subscription> findByNextBillingDate(LocalDate date);
}