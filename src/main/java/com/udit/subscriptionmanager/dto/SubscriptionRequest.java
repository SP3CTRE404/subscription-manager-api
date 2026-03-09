package com.udit.subscriptionmanager.dto;

import com.udit.subscriptionmanager.entity.BillingCycle;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
public class SubscriptionRequest {
    private String serviceName;
    private BigDecimal amount;
    private BillingCycle billingCycle;
    private Integer customIntervalDays;
    private LocalDate nextBillingDate;
    private Long userId;        // The user who owns it
    private Long householdId;   // Optional: The household it belongs to
}