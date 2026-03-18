package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.dto.SubscriptionRequest;
import com.udit.subscriptionmanager.dto.SubscriptionResponse;
import com.udit.subscriptionmanager.entity.*;
import com.udit.subscriptionmanager.repository.HouseholdRepository;
import com.udit.subscriptionmanager.repository.SubscriptionRepository;
import com.udit.subscriptionmanager.repository.SubscriptionHistoryRepository;
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
    private final SubscriptionHistoryRepository subscriptionHistoryRepository;

    @Transactional
    public SubscriptionResponse createSubscription(SubscriptionRequest request) {
        // 1. STRICT ENFORCEMENT: A subscription MUST belong to a specific person
        if (request.getUserId() == null) {
            throw new RuntimeException("A subscription must belong to a specific user.");
        }

        User owner = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Subscription.SubscriptionBuilder builder = Subscription.builder()
                .serviceName(request.getServiceName())
                .amount(request.getAmount())
                .billingCycle(request.getBillingCycle())
                .customIntervalDays(request.getCustomIntervalDays())
                .nextBillingDate(request.getNextBillingDate())
                .isAutoPay(request.getIsAutoPay() != null ? request.getIsAutoPay() : Boolean.TRUE)
                .user(owner); // Assigns the subscription to the payer in the database

        // 2. Optional: Link to household for Admin viewing
        if (request.getHouseholdId() != null) {
            Household household = householdRepository.findById(request.getHouseholdId())
                    .orElseThrow(() -> new RuntimeException("Household not found"));
            builder.household(household);
        }

        Subscription savedSub = subscriptionRepository.save(builder.build());
        return convertToResponse(savedSub);
    }

    /**
     * Calculates the total monthly "burn rate" for a specific user.
     * Uses a strict "Payer Model": The user assumes 100% of the cost for any
     * subscription they own, even if it is linked to a household.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalMonthlyCostForUser(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Fetch ONLY the subscriptions where this user is the payer
        List<Subscription> userSubs = subscriptionRepository.findByUserId(userId);

        // Sum their monthly equivalents without any split logic
        return userSubs.stream()
                .map(this::calculateMonthlyEquivalent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SubscriptionResponse convertToResponse(Subscription sub) {
        return SubscriptionResponse.builder()
                .id(sub.getId())
                .serviceName(sub.getServiceName())
                .amount(sub.getAmount())
                .billingCycle(sub.getBillingCycle())
                .nextBillingDate(sub.getNextBillingDate())
                .isAutoPay(sub.getIsAutoPay() != null ? sub.getIsAutoPay() : Boolean.TRUE)
                .ownerId(sub.getUser() != null ? sub.getUser().getId() : null)               // Maps the ID
                .ownerName(sub.getUser() != null ? sub.getUser().getFullName() : null)       // Maps the Full Name
                .ownerEmail(sub.getUser() != null ? sub.getUser().getEmail() : null)
                .householdName(sub.getHousehold() != null ? sub.getHousehold().getName() : null)
                .build();
    }

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

            // Skip manual payment subscriptions - they remain "past due" until confirmed by the user.
            if (sub.getIsAutoPay() != null && !sub.getIsAutoPay()) {
                continue;
            }

            LocalDate historyDate = sub.getNextBillingDate();
            LocalDate newDate = calculateNextDate(historyDate, sub.getBillingCycle(), sub.getCustomIntervalDays());

            recordHistory(sub, historyDate);

            // Safety measure: if the cycle is very small, and it's far behind, optionally loop until in the future.
            while (newDate.isBefore(today)) {
                historyDate = newDate;
                newDate = calculateNextDate(newDate, sub.getBillingCycle(), sub.getCustomIntervalDays());
                recordHistory(sub, historyDate);
            }

            sub.setNextBillingDate(newDate);
            subscriptionRepository.save(sub);

            log.info("Renewed subscription '{}' (ID: {}). New billing date is {}.", sub.getServiceName(), sub.getId(), newDate);
            renewalCount++;
        }

        log.info("Finished automatic renewal process. Renewed {} subscriptions.", renewalCount);
    }

    private void recordHistory(Subscription sub, LocalDate paymentDate) {
        SubscriptionHistory history = SubscriptionHistory.builder()
                .subscription(sub)
                .amount(sub.getAmount())
                .paymentDate(paymentDate)
                .recordedAt(java.time.LocalDateTime.now())
                .build();
        subscriptionHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionHistory> getHistory(Long subscriptionId) {
        return subscriptionHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscriptionId);
    }

    @Transactional
    public SubscriptionResponse confirmManualPayment(Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        if (sub.getIsAutoPay() != null && sub.getIsAutoPay()) {
            throw new RuntimeException("Cannot manually confirm an auto-pay subscription");
        }

        LocalDate today = LocalDate.now();
        if (sub.getNextBillingDate().isAfter(today)) {
            throw new RuntimeException("Subscription is not due yet");
        }

        LocalDate historyDate = sub.getNextBillingDate();
        LocalDate newDate = calculateNextDate(historyDate, sub.getBillingCycle(), sub.getCustomIntervalDays());

        recordHistory(sub, historyDate);

        // Loop forward if they missed multiple payments
        while (newDate.isBefore(today)) {
            historyDate = newDate;
            newDate = calculateNextDate(newDate, sub.getBillingCycle(), sub.getCustomIntervalDays());
            recordHistory(sub, historyDate);
        }

        sub.setNextBillingDate(newDate);
        Subscription savedSub = subscriptionRepository.save(sub);

        log.info("Manually confirmed payment for subscription '{}' (ID: {}). New billing date is {}.", sub.getServiceName(), sub.getId(), newDate);

        return convertToResponse(savedSub);
    }

    @Transactional
    public SubscriptionResponse toggleAutoPay(Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        sub.setIsAutoPay(!sub.getIsAutoPay());

        Subscription savedSub = subscriptionRepository.save(sub);
        return convertToResponse(savedSub);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getDueSubscriptionsForUser(Long userId) {
        LocalDate today = LocalDate.now();

        return subscriptionRepository.findByNextBillingDateBefore(today).stream()
                .filter(sub -> (sub.getUser() != null && sub.getUser().getId().equals(userId)) ||
                        (sub.getHousehold() != null && sub.getHousehold().getAdmin().getId().equals(userId)))
                .filter(sub -> sub.getIsAutoPay() != null && !sub.getIsAutoPay())
                .map(this::convertToResponse)
                .toList();
    }
}