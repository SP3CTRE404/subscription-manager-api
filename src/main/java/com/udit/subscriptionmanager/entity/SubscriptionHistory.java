package com.udit.subscriptionmanager.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_history", indexes = {
    @Index(name = "idx_subscription_history_sub_date", columnList = "subscription_id, paymentDate DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "subscription_history_gen")
    @SequenceGenerator(name = "subscription_history_gen", sequenceName = "subscription_history_id_seq", initialValue = 500000000, allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    @JsonIgnore
    private Subscription subscription;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

}
