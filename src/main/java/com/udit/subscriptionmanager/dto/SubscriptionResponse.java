package com.udit.subscriptionmanager.dto;

import com.udit.subscriptionmanager.entity.BillingCycle;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @Builder
public class SubscriptionResponse {
    private Long id;
    private String serviceName;
    private BigDecimal amount;
    private BillingCycle billingCycle;
    private LocalDate nextBillingDate;
    private Boolean isAutoPay;
    private String ownerEmail; // Cleaner than returning the whole User object
    private String householdName; // Cleaner than returning the whole Household object
}