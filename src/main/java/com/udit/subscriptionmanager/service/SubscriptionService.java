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
import com.udit.subscriptionmanager.exception.BadRequestException;
import com.udit.subscriptionmanager.exception.ResourceNotFoundException;
import com.udit.subscriptionmanager.exception.UnauthorizedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.lang.NonNull;
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
        log.info("Creating subscription: {} for user ID: {}", request.getServiceName(), request.getUserId());
        // 1. STRICT ENFORCEMENT: A subscription MUST belong to a specific person

        if (request.getUserId() == null) {
            throw new BadRequestException("A subscription must belong to a specific user.");
        }

        Long userId = request.getUserId();
        User owner = userRepository.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getBillingCycle() == BillingCycle.CUSTOM && (request.getCustomIntervalDays() == null || request.getCustomIntervalDays() <= 0)) {
            throw new BadRequestException("Custom interval days are required and must be greater than 0 for CUSTOM billing cycle.");
        }

        Subscription.SubscriptionBuilder builder = Subscription.builder()
                .serviceName(request.getServiceName())
                .amount(request.getAmount())
                .billingCycle(request.getBillingCycle())
                .customIntervalDays(request.getCustomIntervalDays())
                .nextBillingDate(request.getNextBillingDate())
                .purchaseDate(request.getPurchaseDate())
                .isAutoPay(request.getIsAutoPay() != null ? request.getIsAutoPay() : Boolean.TRUE)
                .user(owner); // Assigns the subscription to the payer in the database

        // AUTO-LINK: Always link to the user's household if they have one for visibility
        if (owner.getHousehold() != null) {
            builder.household(owner.getHousehold());
        }


        Subscription subscriptionToSave = builder.build();
        Subscription savedSub = subscriptionRepository.save(java.util.Objects.requireNonNull(subscriptionToSave));
        return convertToResponse(savedSub);
    }

    /**
     * Calculates the total monthly "burn rate" for a specific user.
     * Uses a strict "Payer Model": The user assumes 100% of the cost for any
     * subscription they own, even if it is linked to a household.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalMonthlyCostForUser(@NonNull Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Long householdId = user.getHousehold() != null ? user.getHousehold().getId() : null;
        List<Subscription> visibleSubs;

        if (householdId != null) {
            // Fetch subscriptions where this user is the owner OR which are shared via household
            visibleSubs = subscriptionRepository.findByUserIdOrHouseholdId(userId, householdId);
        } else {
            // Solo user: only their personal subscriptions
            visibleSubs = subscriptionRepository.findByUserId(userId);
        }

        return visibleSubs.stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus() != null ? sub.getStatus() : "ACTIVE"))
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
                .purchaseDate(sub.getPurchaseDate())
                .customIntervalDays(sub.getCustomIntervalDays())
                .isAutoPay(sub.getIsAutoPay() != null ? sub.getIsAutoPay() : Boolean.TRUE)
                .ownerId(sub.getUser() != null ? sub.getUser().getId() : null)               // Maps the ID
                .ownerName(sub.getUser() != null ? sub.getUser().getFullName() : null)       // Maps the Full Name
                .ownerEmail(sub.getUser() != null ? sub.getUser().getEmail() : null)
                .householdName(sub.getHousehold() != null ? sub.getHousehold().getName() : null)
                .householdId(sub.getHousehold() != null ? sub.getHousehold().getId() : null)
                .status(sub.getStatus() != null ? sub.getStatus() : "ACTIVE")
                .build();

    }

    public BigDecimal calculateMonthlyEquivalent(Subscription sub) {
        return switch (sub.getBillingCycle()) {
            case MONTHLY -> sub.getAmount();
            case QUARTERLY -> sub.getAmount().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case YEARLY -> sub.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case CUSTOM -> {
                if (sub.getCustomIntervalDays() == null || sub.getCustomIntervalDays() <= 0) {
                    throw new BadRequestException("Invalid custom interval days");
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
                if (customDays == null) throw new BadRequestException("Custom days required for CUSTOM cycle");
                yield lastDate.plusDays(customDays);
            }
        };
    }

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processAutomaticRenewals() {
        log.info("Starting automatic renewal process for expired subscriptions.");

        LocalDate today = LocalDate.now();
        List<Subscription> expiredSubscriptions = subscriptionRepository.findByNextBillingDateBefore(today).stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus() != null ? sub.getStatus() : "ACTIVE"))
                .toList();

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
        subscriptionHistoryRepository.save(java.util.Objects.requireNonNull(history));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionHistory> getHistory(@NonNull Long subscriptionId) {
        return subscriptionHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscriptionId);
    }

    @Transactional
    public SubscriptionResponse confirmManualPayment(@NonNull Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (sub.getIsAutoPay() != null && sub.getIsAutoPay()) {
            throw new BadRequestException("Cannot manually confirm an auto-pay subscription");
        }

        LocalDate today = LocalDate.now();
        if (sub.getNextBillingDate().isAfter(today)) {
            throw new BadRequestException("Subscription is not due yet");
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
    public SubscriptionResponse toggleAutoPay(@NonNull Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        sub.setIsAutoPay(!sub.getIsAutoPay());

        Subscription savedSub = subscriptionRepository.save(sub);
        return convertToResponse(savedSub);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getDueSubscriptionsForUser(@NonNull Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Subscription> subs;
        if (user.getHousehold() != null) {
            subs = subscriptionRepository.findByMemberHouseholdId(user.getHousehold().getId());
        } else {
            subs = subscriptionRepository.findByUserId(userId);
        }

        LocalDate today = LocalDate.now();

        return subs.stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus() != null ? sub.getStatus() : "ACTIVE"))
                .filter(sub -> sub.getNextBillingDate() != null && sub.getNextBillingDate().isBefore(today))
                .filter(sub -> sub.getIsAutoPay() != null && !sub.getIsAutoPay())
                .map(this::convertToResponse)
                .toList();
    }



    // --- Gap 2.3: Get ALL subscriptions for a user (not just overdue) ---
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getAllSubscriptionsForUser(@NonNull Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Subscription> subs;
        if (user.getHousehold() != null) {
            // Fetch everything belonging to anyone in the household
            subs = subscriptionRepository.findByMemberHouseholdId(user.getHousehold().getId());
        } else {
            // Solo user: only their personal subs
            subs = subscriptionRepository.findByUserId(userId);
        }

        return subs.stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus() != null ? sub.getStatus() : "ACTIVE"))
                .map(this::convertToResponse)
                .toList();
    }




    // --- Gap 2.2: Update a subscription ---
    @Transactional
    public SubscriptionResponse updateSubscription(Long subscriptionId, SubscriptionRequest request, User loggedInUser) {
        Subscription sub = subscriptionRepository.findById(java.util.Objects.requireNonNull(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        // Verify ownership OR Household Admin permission
        boolean isOwner = sub.getUser() != null && sub.getUser().getId().equals(loggedInUser.getId());
        boolean isAdminOfOwner = loggedInUser.getHousehold() != null && 
                                 sub.getUser() != null &&
                                 sub.getUser().getHousehold() != null &&
                                 loggedInUser.getHousehold().getId() == sub.getUser().getHousehold().getId() &&
                                 loggedInUser.isHouseholdAdmin();

        if (!isOwner && !isAdminOfOwner) {
            throw new RuntimeException("Access Denied: You cannot modify this subscription.");
        }


        if (request.getServiceName() != null && !request.getServiceName().isBlank()) {
            sub.setServiceName(request.getServiceName());
        }
        if (request.getAmount() != null) {
            sub.setAmount(request.getAmount());
        }
        if (request.getBillingCycle() != null) {
            sub.setBillingCycle(request.getBillingCycle());
        }
        if (request.getCustomIntervalDays() != null) {
            sub.setCustomIntervalDays(request.getCustomIntervalDays());
        }
        if (request.getNextBillingDate() != null) {
            sub.setNextBillingDate(request.getNextBillingDate());
        }
        if (request.getPurchaseDate() != null) {
            sub.setPurchaseDate(request.getPurchaseDate());
        }
        if (request.getIsAutoPay() != null) {
            sub.setIsAutoPay(request.getIsAutoPay());
        }

        // VALIDATION: BillingCycle.CUSTOM check
        BillingCycle effectiveCycle = sub.getBillingCycle();
        Integer effectiveDays = sub.getCustomIntervalDays();

        if (effectiveCycle == BillingCycle.CUSTOM && (effectiveDays == null || effectiveDays <= 0)) {
            throw new BadRequestException("Custom interval days are required and must be greater than 0 for CUSTOM billing cycle.");
        }

        // Handle household linking/unlinking
        if (request.getHouseholdId() != null) {
            Household household = householdRepository.findById(java.util.Objects.requireNonNull(request.getHouseholdId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Household not found"));
            sub.setHousehold(household);
        } else {
            // Fix: Allow unsharing by setting household to null
            sub.setHousehold(null);
        }


        Subscription saved = subscriptionRepository.save(sub);
        return convertToResponse(saved);
    }

    @Transactional
    public void deleteSubscription(Long subscriptionId, User loggedInUser) {
        Subscription sub = subscriptionRepository.findById(java.util.Objects.requireNonNull(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        // Verify ownership
        if (sub.getUser() == null || !sub.getUser().getId().equals(loggedInUser.getId())) {
            throw new UnauthorizedException("Access Denied: You can only delete your own subscriptions.");
        }

        subscriptionRepository.delete(sub);
    }

    // NEW METHOD: Explicitly Expire a subscription
    @Transactional
    public SubscriptionResponse expireSubscription(Long subscriptionId, User loggedInUser) {
        Subscription sub = subscriptionRepository.findById(java.util.Objects.requireNonNull(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (sub.getUser() == null || !sub.getUser().getId().equals(loggedInUser.getId())) {
            throw new UnauthorizedException("Access Denied: You can only expire your own subscriptions.");
        }

        sub.setStatus("EXPIRED");
        // Mark exactly when it ended
        sub.setNextBillingDate(LocalDate.now());

        Subscription saved = subscriptionRepository.save(sub);
        return convertToResponse(saved);
    }

    // NEW METHOD: Fetch only Expired Subscriptions for the History Tab
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getExpiredSubscriptionsForUser(@NonNull Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Long householdId = user.getHousehold() != null ? user.getHousehold().getId() : null;
        List<Subscription> subs;

        if (householdId != null) {
            subs = subscriptionRepository.findByUserIdOrHouseholdId(userId, householdId);
        } else {
            subs = subscriptionRepository.findByUserId(userId);
        }

        return subs.stream()
                .filter(sub -> "EXPIRED".equals(sub.getStatus()))
                .map(this::convertToResponse)
                .toList();
    }


    // --- Gap 2.6: Get all subscriptions for a household ---
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getSubscriptionsForHousehold(@NonNull Long householdId) {
        return subscriptionRepository.findByMemberHouseholdId(householdId)
                .stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus() != null ? sub.getStatus() : "ACTIVE"))
                .map(this::convertToResponse)
                .toList();
    }

}