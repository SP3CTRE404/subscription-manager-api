package com.udit.subscriptionmanager.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    
    // Standard ISO-8601 date format from Flutter: YYYY-MM-DD
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate nextBillingDate;

    private Boolean isAutoPay;          // Can specify if this is an auto-renewing sub
    private Long userId;        // The user who owns it
    private Long householdId;   // Optional: The household it belongs to
}