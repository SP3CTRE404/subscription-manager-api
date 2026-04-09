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
        
        if (!loggedInUser.getId().equals(requestedUserId)) {
            throw new RuntimeException("Access Denied: You cannot view or modify another user's data.");
        }
    }

    @PostMapping("/add")
    public ResponseEntity<SubscriptionResponse> addSubscription(
            @RequestBody SubscriptionRequest request, 
            Authentication authentication) {
        
        // 1. Get the logged-in user's email from the JWT token
        User loggedInUser = getLoggedInUser(authentication);
                
        // 2. Manually set the userId in the request payload
        request.setUserId(loggedInUser.getId());
        
        return ResponseEntity.ok(subscriptionService.createSubscription(request));
    }

    // --- Gap 2.3: Get ALL subscriptions for a user (not just overdue) ---
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SubscriptionResponse>> getAllSubscriptions(
            @PathVariable Long userId,
            Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.getAllSubscriptionsForUser(userId));
    }

    @GetMapping("/user/{userId}/monthly-total")
    public ResponseEntity<BigDecimal> getMonthlyTotal(@PathVariable Long userId, Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.calculateTotalMonthlyCostForUser(java.util.Objects.requireNonNull(userId)));
    }

    @GetMapping("/user/{userId}/due")
    public ResponseEntity<List<SubscriptionResponse>> getDueSubscriptions(@PathVariable Long userId, Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.getDueSubscriptionsForUser(userId));
    }

    // NEW: Fetch expired history
    @GetMapping("/user/{userId}/expired")
    public ResponseEntity<List<SubscriptionResponse>> getExpiredSubscriptions(
            @PathVariable Long userId,
            Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.getExpiredSubscriptionsForUser(userId));
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
        return ResponseEntity.ok(subscriptionService.getSubscriptionsForHousehold(householdId));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<SubscriptionHistory>> getSubscriptionHistory(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getHistory(id));
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