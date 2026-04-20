package com.udit.subscriptionmanager.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum BillingCycle {
    MONTHLY,
    QUARTERLY,
    YEARLY,
    CUSTOM,
    ONE_TIME;

    // This tells Spring Boot to accept lowercase, uppercase, or mixed case strings
    @JsonCreator
    public static BillingCycle fromString(String value) {
        if (value == null) {
            return null;
        }
        return BillingCycle.valueOf(value.toUpperCase());
    }
}