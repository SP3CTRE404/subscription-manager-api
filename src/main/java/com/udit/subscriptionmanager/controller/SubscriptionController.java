package com.udit.subscriptionmanager.controller;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.udit.subscriptionmanager.dto.SubscriptionRequest;
import com.udit.subscriptionmanager.dto.SubscriptionResponse;
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
    public ResponseEntity<java.util.List<com.udit.subscriptionmanager.entity.SubscriptionHistory>> getSubscriptionHistory(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getHistory(id));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<SubscriptionResponse> confirmManualPayment(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.confirmManualPayment(id));
    }
}