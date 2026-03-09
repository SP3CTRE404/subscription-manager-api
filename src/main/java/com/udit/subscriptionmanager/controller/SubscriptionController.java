package com.udit.subscriptionmanager.controller;

import com.udit.subscriptionmanager.dto.SubscriptionRequest;
import com.udit.subscriptionmanager.dto.SubscriptionResponse;
import com.udit.subscriptionmanager.entity.Subscription;
import com.udit.subscriptionmanager.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

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
}