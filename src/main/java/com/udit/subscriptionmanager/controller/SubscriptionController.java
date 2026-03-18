package com.udit.subscriptionmanager.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private void verifyUserAccess(Long requestedUserId, Authentication authentication) {
        String loggedInEmail = authentication.getName(); // Gets email from the JWT
        User loggedInUser = userService.findByEmail(loggedInEmail)
                .orElseThrow(() -> new RuntimeException("Logged in user not found"));
        
        if (!loggedInUser.getId().equals(requestedUserId)) {
            throw new RuntimeException("Access Denied: You cannot view or modify another user's data.");
        }
    }

    @PostMapping("/add")
    public ResponseEntity<SubscriptionResponse> addSubscription(@RequestBody SubscriptionRequest request, Authentication authentication) {
        // Prevent User A from adding a subscription to User B's account
        verifyUserAccess(request.getUserId(), authentication);
        return ResponseEntity.ok(subscriptionService.createSubscription(request));
    }

    @GetMapping("/user/{userId}/monthly-total")
    public ResponseEntity<BigDecimal> getMonthlyTotal(@PathVariable Long userId, Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.calculateTotalMonthlyCostForUser(userId));
    }

    @GetMapping("/user/{userId}/due")
    public ResponseEntity<List<SubscriptionResponse>> getDueSubscriptions(@PathVariable Long userId, Authentication authentication) {
        verifyUserAccess(userId, authentication);
        return ResponseEntity.ok(subscriptionService.getDueSubscriptionsForUser(userId));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<SubscriptionHistory>> getSubscriptionHistory(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getHistory(id));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<SubscriptionResponse> confirmManualPayment(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.confirmManualPayment(id));
    }

    @PostMapping("/{id}/toggle-autopay")
    public ResponseEntity<SubscriptionResponse> toggleAutoPay(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.toggleAutoPay(id));
    }
}