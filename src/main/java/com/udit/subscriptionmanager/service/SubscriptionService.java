package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.dto.SubscriptionRequest;
import com.udit.subscriptionmanager.dto.SubscriptionResponse;
import com.udit.subscriptionmanager.entity.*;
import com.udit.subscriptionmanager.repository.HouseholdRepository;
import com.udit.subscriptionmanager.repository.SubscriptionRepository;
import com.udit.subscriptionmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;

    /**
     * Proper Fix: Converts the Entity to a DTO before returning it.
     * This avoids LazyInitializationException by accessing relationships
     * while the Hibernate session is still open.
     */
    @Transactional
    public SubscriptionResponse createSubscription(SubscriptionRequest request) {
        Subscription.SubscriptionBuilder builder = Subscription.builder()
                .serviceName(request.getServiceName())
                .amount(request.getAmount())
                .billingCycle(request.getBillingCycle())
                .customIntervalDays(request.getCustomIntervalDays())
                .nextBillingDate(request.getNextBillingDate());

        // Link to User if provided (Solo/Private subscription)
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            builder.user(user);
        }

        // Link to Household if provided (Shared subscription)
        if (request.getHouseholdId() != null) {
            Household household = householdRepository.findById(request.getHouseholdId())
                    .orElseThrow(() -> new RuntimeException("Household not found"));
            builder.household(household);
        }

        Subscription savedSub = subscriptionRepository.save(builder.build());
        return convertToResponse(savedSub);
    }

    /**
     * Calculates the total monthly "burn rate" for a specific user,
     * including their solo subs and their share of household subs.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalMonthlyCostForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Get solo subscriptions
        List<Subscription> soloSubs = subscriptionRepository.findByUserId(userId);
        BigDecimal soloTotal = soloSubs.stream()
                .map(this::calculateMonthlyEquivalent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Get household subscriptions (if any)
        BigDecimal householdShare = BigDecimal.ZERO;
        if (user.getHousehold() != null) {
            List<Subscription> householdSubs = subscriptionRepository.findByHouseholdId(user.getHousehold().getId());
            int memberCount = user.getHousehold().getMembers().size();

            if (memberCount > 0) {
                BigDecimal totalHouseholdMonthly = householdSubs.stream()
                        .map(this::calculateMonthlyEquivalent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Divide total household cost by number of members
                householdShare = totalHouseholdMonthly.divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP);
            }
        }

        return soloTotal.add(householdShare);
    }

    /**
     * Helper to map Entity to DTO safely.
     */
    private SubscriptionResponse convertToResponse(Subscription sub) {
        return SubscriptionResponse.builder()
                .id(sub.getId())
                .serviceName(sub.getServiceName())
                .amount(sub.getAmount())
                .billingCycle(sub.getBillingCycle())
                .nextBillingDate(sub.getNextBillingDate())
                .ownerEmail(sub.getUser() != null ? sub.getUser().getEmail() : null)
                .householdName(sub.getHousehold() != null ? sub.getHousehold().getName() : null)
                .build();
    }

    /**
     * Normalises all costs to a "Monthly" average for budgeting.
     */
    public BigDecimal calculateMonthlyEquivalent(Subscription sub) {
        return switch (sub.getBillingCycle()) {
            case MONTHLY -> sub.getAmount();
            case QUARTERLY -> sub.getAmount().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case YEARLY -> sub.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case CUSTOM -> {
                if (sub.getCustomIntervalDays() == null || sub.getCustomIntervalDays() <= 0) {
                    throw new RuntimeException("Invalid custom interval days");
                }
                // (Amount / days) * 30 days
                BigDecimal daily = sub.getAmount().divide(BigDecimal.valueOf(sub.getCustomIntervalDays()), 4, RoundingMode.HALF_UP);
                yield daily.multiply(BigDecimal.valueOf(30)).setScale(2, RoundingMode.HALF_UP);
            }
        };
    }

    /**
     * Calculates the next billing date based on the cycle type.
     */
    public LocalDate calculateNextDate(LocalDate lastDate, BillingCycle cycle, Integer customDays) {
        return switch (cycle) {
            case MONTHLY -> lastDate.plusMonths(1);
            case QUARTERLY -> lastDate.plusMonths(3);
            case YEARLY -> lastDate.plusYears(1);
            case CUSTOM -> {
                if (customDays == null) throw new RuntimeException("Custom days required for CUSTOM cycle");
                yield lastDate.plusDays(customDays);
            }
        };
    }

    /**
     * Periodically checks for subscriptions whose billing date has passed 
     * and automatically increments their nextBillingDate.
     * Runs daily at 1:00 AM server time.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processAutomaticRenewals() {
        log.info("Starting automatic renewal process for expired subscriptions.");
        
        LocalDate today = LocalDate.now();
        List<Subscription> expiredSubscriptions = subscriptionRepository.findByNextBillingDateBefore(today);
        
        int renewalCount = 0;
        for (Subscription sub : expiredSubscriptions) {
            if (sub.getNextBillingDate() == null || sub.getBillingCycle() == null) {
                log.warn("Subscription {} is missing date or cycle info.", sub.getId());
                continue;
            }
            
            LocalDate newDate = calculateNextDate(sub.getNextBillingDate(), sub.getBillingCycle(), sub.getCustomIntervalDays());
            
            // Safety measure: if the cycle is very small and it's far behind, optionally loop until in future.
            // For now, one simple increment is expected as the job runs daily.
            while (newDate.isBefore(today)) {
                newDate = calculateNextDate(newDate, sub.getBillingCycle(), sub.getCustomIntervalDays());
            }

            sub.setNextBillingDate(newDate);
            subscriptionRepository.save(sub);
            
            log.info("Renewed subscription '{}' (ID: {}). New billing date is {}.", sub.getServiceName(), sub.getId(), newDate);
            renewalCount++;
        }
        
        log.info("Finished automatic renewal process. Renewed {} subscriptions.", renewalCount);
    }
}