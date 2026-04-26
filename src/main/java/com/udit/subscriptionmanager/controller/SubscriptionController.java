package com.udit.subscriptionmanager.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.udit.subscriptionmanager.dto.SubscriptionRequest;
import com.udit.subscriptionmanager.dto.SubscriptionResponse;
import com.udit.subscriptionmanager.entity.SubscriptionHistory;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.service.SubscriptionService;
import com.udit.subscriptionmanager.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserService userService; // NEW: Added to look up the logged-in user

    // --- SECURITY HELPER METHOD ---
    private User getLoggedInUser(Authentication authentication) {
        String loggedInEmail = authentication.getName();
        return userService.findByEmail(loggedInEmail)
                .orElseThrow(() -> new RuntimeException("Logged in user not found"));
    }

    private void verifyUserAccess(Long requestedUserId, Authentication authentication) {
        User loggedInUser = getLoggedInUser(authentication);
        
        // 1. You can always access your own data
        if (loggedInUser.getId().equals(requestedUserId)) {
            return;
        }

        // 2. Household Admin can access data of their household members
        User targetUser = userService.findById(java.util.Objects.requireNonNull(requestedUserId))
                .orElseThrow(() -> new RuntimeException("Target user find failure"));

        boolean sameHousehold = loggedInUser.getHousehold() != null && 
                               targetUser.getHousehold() != null &&
                               loggedInUser.getHousehold().getId() == targetUser.getHousehold().getId();

        
        boolean isAdmin = loggedInUser.isHouseholdAdmin();

        if (!isAdmin || !sameHousehold) {
            throw new RuntimeException("Access Denied: You cannot view or modify this user's data.");
        }
    }


    @PostMapping("/add")
    public ResponseEntity<SubscriptionResponse> addSubscription(
            @RequestBody SubscriptionRequest request, 
            Authentication authentication) {
        
        User loggedInUser = getLoggedInUser(authentication);
        
        // If userId is missing, default to the logged-in user
        if (request.getUserId() == null) {
            request.setUserId(loggedInUser.getId());
        } else if (!request.getUserId().equals(loggedInUser.getId())) {
            // If adding for someone else, verify permission
            verifyUserAccess(request.getUserId(), authentication);
        }
        
        return ResponseEntity.ok(subscriptionService.createSubscription(request));
    }


    // --- Gap 2.3: Get ALL subscriptions for a user (not just overdue) ---
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SubscriptionResponse>> getAllSubscriptions(
            @PathVariable Long userId,
            Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.getAllSubscriptionsForUser(java.util.Objects.requireNonNull(userId)));
    }

    @GetMapping("/user/{userId}/monthly-total")
    public ResponseEntity<BigDecimal> getMonthlyTotal(@PathVariable Long userId, Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.calculateTotalMonthlyCostForUser(java.util.Objects.requireNonNull(userId)));
    }

    @GetMapping("/user/{userId}/due")
    public ResponseEntity<List<SubscriptionResponse>> getDueSubscriptions(@PathVariable Long userId, Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.getDueSubscriptionsForUser(java.util.Objects.requireNonNull(userId)));
    }

    // NEW: Fetch expired history
    @GetMapping("/user/{userId}/expired")
    public ResponseEntity<List<SubscriptionResponse>> getExpiredSubscriptions(
            @PathVariable Long userId,
            Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.getExpiredSubscriptionsForUser(java.util.Objects.requireNonNull(userId)));
    }

    @GetMapping("/user/{userId}/history")
    public ResponseEntity<Map<String, Object>> getUserHistory(
            @PathVariable Long userId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        verifyUserAccess(userId, authentication);
        List<SubscriptionHistory> allHistory = subscriptionService.getUserHistory(java.util.Objects.requireNonNull(userId));
        
        // Manual pagination over the full list
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allHistory.size());
        
        List<SubscriptionHistory> pageContent = (fromIndex < allHistory.size()) 
                ? allHistory.subList(fromIndex, toIndex) 
                : List.of();
        
        Map<String, Object> response = Map.of(
                "content", pageContent,
                "page", page,
                "size", size,
                "totalElements", allHistory.size(),
                "hasMore", toIndex < allHistory.size()
        );
        return ResponseEntity.ok(response);
    }

    // --- Gap 2.2: Update a subscription ---
    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> updateSubscription(
            @PathVariable Long id,
            @RequestBody SubscriptionRequest request,
            Authentication authentication) {
        User loggedInUser = getLoggedInUser(authentication);
        return ResponseEntity.ok(subscriptionService.updateSubscription(id, request, loggedInUser));
    }

    // NEW: Expire a subscription
    @PutMapping("/{id}/expire")
    public ResponseEntity<SubscriptionResponse> expireSubscription(
            @PathVariable Long id,
            Authentication authentication) {
        User loggedInUser = getLoggedInUser(authentication);
        return ResponseEntity.ok(subscriptionService.expireSubscription(id, loggedInUser));
    }

    // --- Gap 2.2: Delete a subscription ---
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteSubscription(
            @PathVariable Long id,
            Authentication authentication) {
        User loggedInUser = getLoggedInUser(authentication);
        subscriptionService.deleteSubscription(id, loggedInUser);
        return ResponseEntity.ok(Map.of("message", "Subscription deleted successfully."));
    }

    // --- Gap 2.6: Get all subscriptions for a household ---
    @GetMapping("/household/{householdId}")
    public ResponseEntity<List<SubscriptionResponse>> getHouseholdSubscriptions(
            @PathVariable Long householdId,
            Authentication authentication) {
        // Verify the user belongs to this household
        User loggedInUser = getLoggedInUser(authentication);
        if (loggedInUser.getHousehold() == null || loggedInUser.getHousehold().getId() != householdId) {
            throw new RuntimeException("Access Denied: You can only view subscriptions for your own household.");
        }
        return ResponseEntity.ok(subscriptionService.getSubscriptionsForHousehold(java.util.Objects.requireNonNull(householdId)));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<SubscriptionHistory>> getSubscriptionHistory(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getHistory(java.util.Objects.requireNonNull(id)));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<SubscriptionResponse> confirmManualPayment(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.confirmManualPayment(java.util.Objects.requireNonNull(id)));
    }

    @PostMapping("/{id}/toggle-autopay")
    public ResponseEntity<SubscriptionResponse> toggleAutoPay(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.toggleAutoPay(java.util.Objects.requireNonNull(id)));
    }
}