package com.udit.subscriptionmanager.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.udit.subscriptionmanager.dto.SubscriptionRequest;
import com.udit.subscriptionmanager.dto.SubscriptionResponse;
import com.udit.subscriptionmanager.entity.SubscriptionHistory;
import com.udit.subscriptionmanager.service.SubscriptionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/add")
    public ResponseEntity<SubscriptionResponse> addSubscription(@RequestBody SubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.createSubscription(request));
    }

    @GetMapping("/user/{userId}/monthly-total")
    public ResponseEntity<BigDecimal> getMonthlyTotal(@PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionService.calculateTotalMonthlyCostForUser(userId));
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

    @GetMapping("/user/{userId}/due")
    public ResponseEntity<List<SubscriptionResponse>> getDueSubscriptions(@PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionService.getDueSubscriptionsForUser(userId));
    }
}