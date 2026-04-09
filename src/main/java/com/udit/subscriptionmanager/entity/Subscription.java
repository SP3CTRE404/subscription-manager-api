package com.udit.subscriptionmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingCycle billingCycle;

    // Only used if billingCycle == CUSTOM
    private Integer customIntervalDays;

    @Column(nullable = false)
    private LocalDate nextBillingDate;

    @Column(nullable = false, columnDefinition = "DATE DEFAULT CURRENT_DATE")
    private LocalDate purchaseDate;
    
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isAutoPay = true;

    @Column(nullable = false)
    @Builder.Default
    private String status = "ACTIVE"; // "ACTIVE" or "EXPIRED"

    // A subscription can either belong to one user (Solo Subscription)
    // OR it can belong to a household (Shared Subscription);

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "household_id")
    private Household household;
}