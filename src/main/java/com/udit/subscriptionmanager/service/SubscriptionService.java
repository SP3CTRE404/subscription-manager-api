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
import java.util.Objects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

        if (request.getBillingCycle() == BillingCycle.CUSTOM
                && (request.getCustomIntervalDays() == null || request.getCustomIntervalDays() <= 0)) {
            throw new BadRequestException(
                    "Custom interval days are required and must be greater than 0 for CUSTOM billing cycle.");
        }

        LocalDate initialNextDate = calculateInitialNextBillingDate(
                request.getPurchaseDate(),
                request.getBillingCycle(),
                request.getCustomIntervalDays(),
                request.getCustomIntervalUnit()
        );

        String initialStatus = "ACTIVE";
        if (request.getBillingCycle() == BillingCycle.ONE_TIME && request.getPurchaseDate().isBefore(LocalDate.now())) {
            initialStatus = "EXPIRED";
        }

        Subscription.SubscriptionBuilder builder = Subscription.builder()
                .serviceName(request.getServiceName())
                .amount(request.getAmount())
                .billingCycle(request.getBillingCycle())
                .customIntervalDays(request.getCustomIntervalDays())
                .customIntervalUnit(request.getCustomIntervalUnit())
                .nextBillingDate(initialNextDate)
                .purchaseDate(request.getPurchaseDate())
                .status(initialStatus)
                .isAutoPay(request.getIsAutoPay() != null ? request.getIsAutoPay() : Boolean.TRUE)
                .currency(request.getCurrency())
                .user(owner); // Assigns the subscription to the payer in the database

        // AUTO-LINK: Always link to the user's household if they have one for
        // visibility
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
    @Transactional
    public BigDecimal calculateTotalMonthlyCostForUser(@NonNull Long userId) {
        processAutomaticRenewals();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Long householdId = user.getHousehold() != null ? user.getHousehold().getId() : null;
        List<Subscription> visibleSubs;

        if (householdId != null) {
            // Fetch subscriptions where this user is the owner OR which are shared via
            // household
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
        long days = (sub.getNextBillingDate() != null)
                ? java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), sub.getNextBillingDate())
                : 0;

        boolean isManual = Boolean.FALSE.equals(sub.getIsAutoPay());

        // Rule: 3 days consecutively before (Manual) or 1 day before (Auto)
        // Manual now includes 'due today' (days == 0) to ensure it appears in Action Needed
        boolean upcomingManual = isManual && (days >= 0 && days <= 3);
        boolean upcomingAuto = !isManual && (days == 1);
        boolean isUpcoming = upcomingManual || upcomingAuto;

        // Rule: Everyday that it is overdue (Manual only)
        boolean isOverdue = isManual && days < 0;

        // Rule: Day of payment (Auto-pay renewed notification)
        boolean isRenewedToday = !isManual && days == 0;

        return SubscriptionResponse.builder()
                .id(sub.getId())
                .serviceName(sub.getServiceName())
                .amount(sub.getAmount())
                .billingCycle(sub.getBillingCycle())
                .nextBillingDate(sub.getNextBillingDate())
                .purchaseDate(sub.getPurchaseDate())
                .customIntervalDays(sub.getCustomIntervalDays())
                .customIntervalUnit(sub.getCustomIntervalUnit())
                .isAutoPay(sub.getIsAutoPay() != null ? sub.getIsAutoPay() : Boolean.TRUE)
                .ownerId(sub.getUser() != null ? sub.getUser().getId() : null) // Maps the ID
                .ownerName(sub.getUser() != null ? sub.getUser().getFullName() : null) // Maps the Full Name
                .ownerEmail(sub.getUser() != null ? sub.getUser().getEmail() : null)
                .householdName(sub.getHousehold() != null ? sub.getHousehold().getName() : null)
                .householdId(sub.getHousehold() != null ? sub.getHousehold().getId() : null)
                .status(isRenewedToday ? "RENEWED_TODAY" : (sub.getStatus() != null ? sub.getStatus() : "ACTIVE"))
                .isOverdue(isOverdue)
                .isUpcoming(isUpcoming)
                .daysUntilDue(days)
                .isRenewedToday(isRenewedToday)
                .currency(sub.getCurrency())
                .build();
    }

    public BigDecimal calculateMonthlyEquivalent(Subscription sub) {
        return switch (sub.getBillingCycle()) {
            case MONTHLY -> sub.getAmount();
            case QUARTERLY -> sub.getAmount().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case YEARLY -> sub.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case ONE_TIME -> sub.getAmount();
            case CUSTOM -> {
                if (sub.getCustomIntervalDays() == null || sub.getCustomIntervalDays() <= 0) {
                    throw new BadRequestException("Invalid custom interval value");
                }
                CustomIntervalUnit unit = sub.getCustomIntervalUnit() != null ? sub.getCustomIntervalUnit() : CustomIntervalUnit.DAYS;
                yield switch (unit) {
                    case DAYS -> sub.getAmount()
                            .divide(BigDecimal.valueOf(sub.getCustomIntervalDays()), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(30))
                            .setScale(2, RoundingMode.HALF_UP);
                    case MONTHS -> sub.getAmount()
                            .divide(BigDecimal.valueOf(sub.getCustomIntervalDays()), 2, RoundingMode.HALF_UP);
                    case YEARS -> sub.getAmount()
                            .divide(BigDecimal.valueOf(sub.getCustomIntervalDays() * 12L), 2, RoundingMode.HALF_UP);
                };
            }
        };
    }

    public LocalDate calculateNextDate(LocalDate lastDate, BillingCycle cycle, Integer customVal, CustomIntervalUnit unit) {
        return switch (cycle) {
            case MONTHLY -> lastDate.plusMonths(1);
            case QUARTERLY -> lastDate.plusMonths(3);
            case YEARLY -> lastDate.plusYears(1);
            case CUSTOM -> {
                if (customVal == null)
                    throw new BadRequestException("Custom interval value required for CUSTOM cycle");
                if (unit == null || unit == CustomIntervalUnit.DAYS) {
                    yield lastDate.plusDays(customVal);
                } else if (unit == CustomIntervalUnit.MONTHS) {
                    yield lastDate.plusMonths(customVal);
                } else {
                    yield lastDate.plusYears(customVal);
                }
            }
            case ONE_TIME -> null;
        };
    }

    private LocalDate calculateInitialNextBillingDate(LocalDate purchaseDate, BillingCycle cycle, Integer customVal, CustomIntervalUnit unit) {
        if (cycle == BillingCycle.ONE_TIME) {
            return null;
        }
        LocalDate nextDate = calculateNextDate(purchaseDate, cycle, customVal, unit);
        LocalDate today = LocalDate.now();

        // If the first billing date is already in the future, use it
        if (!nextDate.isBefore(today)) {
            return nextDate;
        }

        // Find the most recent past billing date (so manual payments show as overdue)
        while (true) {
            LocalDate following = calculateNextDate(nextDate, cycle, customVal, unit);
            if (!following.isBefore(today)) {
                // nextDate is the most recent past date — return it
                return nextDate;
            }
            nextDate = following;
        }
    }

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processAutomaticRenewals() {
        log.info("Starting automatic renewal process for expired subscriptions.");

        LocalDate today = LocalDate.now();

        // 1. Expire ONE_TIME subscriptions 1 day after their purchase date
        List<Subscription> oneTimeToExpire = subscriptionRepository.findByStatusAndBillingCycleAndPurchaseDateBefore("ACTIVE", BillingCycle.ONE_TIME, today);
        for (Subscription sub : oneTimeToExpire) {
            sub.setStatus("EXPIRED");
            subscriptionRepository.save(sub);
            log.info("Expired ONE_TIME subscription '{}' (ID: {}).", sub.getServiceName(), sub.getId());
        }

        // 2. Process automatic renewals for recurring subscriptions
        List<Subscription> expiredSubscriptions = subscriptionRepository.findByNextBillingDateBefore(today).stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus() != null ? sub.getStatus() : "ACTIVE"))
                .toList();

        int renewalCount = 0;
        for (Subscription sub : expiredSubscriptions) {
            if (sub.getNextBillingDate() == null || sub.getBillingCycle() == null) {
                log.warn("Subscription {} is missing date or cycle info.", sub.getId());
                continue;
            }

            // Skip manual payment subscriptions - they remain "past due" until confirmed by
            // the user.
            if (sub.getIsAutoPay() != null && !sub.getIsAutoPay()) {
                continue;
            }

            LocalDate historyDate = sub.getNextBillingDate();
            LocalDate newDate = calculateNextDate(historyDate, sub.getBillingCycle(), sub.getCustomIntervalDays(), sub.getCustomIntervalUnit());

            recordHistory(sub, historyDate);

            // Safety measure: if the cycle is very small, and it's far behind, optionally
            // loop until in the future.
            while (newDate.isBefore(today)) {
                historyDate = newDate;
                newDate = calculateNextDate(newDate, sub.getBillingCycle(), sub.getCustomIntervalDays(), sub.getCustomIntervalUnit());
                recordHistory(sub, historyDate);
            }

            sub.setNextBillingDate(newDate);
            subscriptionRepository.save(sub);

            log.info("Renewed subscription '{}' (ID: {}). New billing date is {}.", sub.getServiceName(), sub.getId(),
                    newDate);
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
                .currency(sub.getCurrency())
                .build();
        subscriptionHistoryRepository.save(java.util.Objects.requireNonNull(history));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionHistory> getHistory(@NonNull Long subscriptionId) {
        return subscriptionHistoryRepository.findBySubscription_IdOrderByPaymentDateDesc(subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionHistory> getUserHistory(@NonNull Long userId) {
        return subscriptionHistoryRepository.findBySubscriptionUserIdOrderByPaymentDateDesc(userId);
    }

    @Transactional
    public SubscriptionResponse confirmManualPayment(@NonNull Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (sub.getIsAutoPay() != null && sub.getIsAutoPay()) {
            throw new BadRequestException("Cannot manually confirm an auto-pay subscription");
        }

        LocalDate today = LocalDate.now();

        LocalDate historyDate = sub.getNextBillingDate() != null ? sub.getNextBillingDate() : sub.getPurchaseDate();
        LocalDate newDate = calculateNextDate(historyDate, sub.getBillingCycle(), sub.getCustomIntervalDays(), sub.getCustomIntervalUnit());

        // Record the payment as occurring TODAY (when the user pressed the tick)
        recordHistory(sub, today);

        if (sub.getBillingCycle() == BillingCycle.ONE_TIME) {
            sub.setNextBillingDate(null);
            sub.setStatus("EXPIRED");
            Subscription savedSub = subscriptionRepository.save(sub);
            return convertToResponse(savedSub);
        }

        // Loop forward if they missed multiple payments
        while (newDate != null && newDate.isBefore(today)) {
            historyDate = newDate;
            newDate = calculateNextDate(newDate, sub.getBillingCycle(), sub.getCustomIntervalDays(), sub.getCustomIntervalUnit());
            // Even catch-up payments are recorded as occurring TODAY
            recordHistory(sub, today);
        }

        sub.setNextBillingDate(newDate);
        Subscription savedSub = subscriptionRepository.save(sub);

        log.info("Manually confirmed payment for subscription '{}' (ID: {}). New billing date is {}.",
                sub.getServiceName(), sub.getId(), newDate);

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

    @Transactional
    public List<SubscriptionResponse> getDueSubscriptionsForUser(@NonNull Long userId) {
        processAutomaticRenewals();
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
                .filter(sub -> sub.getNextBillingDate() != null && !sub.getNextBillingDate().isAfter(today))
                .filter(sub -> sub.getIsAutoPay() != null && !sub.getIsAutoPay())
                .map(this::convertToResponse)
                .toList();
    }

    // --- Gap 2.3: Get ALL subscriptions for a user (not just overdue) ---
    @Transactional
    public List<SubscriptionResponse> getAllSubscriptionsForUser(@NonNull Long userId) {
        processAutomaticRenewals();
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
    public SubscriptionResponse updateSubscription(Long subscriptionId, SubscriptionRequest request,
            User loggedInUser) {
        Subscription sub = subscriptionRepository.findById(java.util.Objects.requireNonNull(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        checkSubscriptionAccess(sub, loggedInUser, "modify");

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
        if (request.getCustomIntervalUnit() != null) {
            sub.setCustomIntervalUnit(request.getCustomIntervalUnit());
        }
        if (request.getPurchaseDate() != null) {
            sub.setPurchaseDate(request.getPurchaseDate());
        }
        if (request.getIsAutoPay() != null) {
            sub.setIsAutoPay(request.getIsAutoPay());
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            sub.setCurrency(request.getCurrency());
        }

        // RECALCULATE: If cycle or purchase date changed, and nextBillingDate wasn't explicitly provided, recalculate it.
        if (request.getNextBillingDate() == null && 
           (request.getBillingCycle() != null || request.getPurchaseDate() != null || 
            request.getCustomIntervalDays() != null || request.getCustomIntervalUnit() != null)) {
            
            LocalDate recalculated = calculateInitialNextBillingDate(
                sub.getPurchaseDate(),
                sub.getBillingCycle(),
                sub.getCustomIntervalDays(),
                sub.getCustomIntervalUnit()
            );
            sub.setNextBillingDate(recalculated);
        } else if (request.getNextBillingDate() != null) {
            sub.setNextBillingDate(request.getNextBillingDate());
        }

        // VALIDATION: BillingCycle.CUSTOM check
        BillingCycle effectiveCycle = sub.getBillingCycle();
        Integer effectiveDays = sub.getCustomIntervalDays();

        if (effectiveCycle == BillingCycle.CUSTOM && (effectiveDays == null || effectiveDays <= 0)) {
            throw new BadRequestException(
                    "Custom interval days are required and must be greater than 0 for CUSTOM billing cycle.");
        }

        // Handle household linking/unlinking
        if (request.getHouseholdId() != null) {
            Household household = householdRepository
                    .findById(java.util.Objects.requireNonNull(request.getHouseholdId()))
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

        checkSubscriptionAccess(sub, loggedInUser, "delete");

        subscriptionRepository.delete(java.util.Objects.requireNonNull(sub));
    }

    // NEW METHOD: Explicitly Expire a subscription
    @Transactional
    public SubscriptionResponse expireSubscription(Long subscriptionId, User loggedInUser) {
        Subscription sub = subscriptionRepository.findById(java.util.Objects.requireNonNull(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        checkSubscriptionAccess(sub, loggedInUser, "expire");

        sub.setStatus("EXPIRED");
        // Mark exactly when it ended
        sub.setNextBillingDate(LocalDate.now());

        Subscription saved = subscriptionRepository.save(sub);
        return convertToResponse(saved);
    }

    // NEW METHOD: Fetch only Expired Subscriptions for the History Tab
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getExpiredSubscriptionsForUser(@NonNull Long userId) {
        List<Subscription> subs = subscriptionRepository.findByUserId(userId);

        return subs.stream()
                .filter(sub -> "EXPIRED".equals(sub.getStatus()))
                .map(this::convertToResponse)
                .toList();
    }

    // --- Gap 2.6: Get all subscriptions for a household ---
    @Transactional
    public List<SubscriptionResponse> getSubscriptionsForHousehold(@NonNull Long householdId) {
        processAutomaticRenewals();
        return subscriptionRepository.findByMemberHouseholdId(householdId)
                .stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus() != null ? sub.getStatus() : "ACTIVE"))
                .map(this::convertToResponse)
                .toList();
    }

    private void checkSubscriptionAccess(Subscription sub, User loggedInUser, String action) {
        boolean isOwner = sub.getUser() != null && Objects.equals(sub.getUser().getId(), loggedInUser.getId());
        boolean isAdminOfOwner = loggedInUser.getHousehold() != null &&
                sub.getUser() != null &&
                sub.getUser().getHousehold() != null &&
                Objects.equals(loggedInUser.getHousehold().getId(), sub.getUser().getHousehold().getId()) &&
                loggedInUser.isHouseholdAdmin();

        if (!isOwner && !isAdminOfOwner) {
            throw new UnauthorizedException("Access Denied: You cannot " + action + " this subscription.");
        }
    }
}