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
    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    private String householdName;
    private Long householdId;
    private String status;
}
