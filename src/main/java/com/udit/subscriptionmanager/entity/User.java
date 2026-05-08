package com.udit.subscriptionmanager.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_gen")
    @SequenceGenerator(name = "user_gen", sequenceName = "user_id_seq", initialValue = 200000000, allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Builder.Default
    private String timeZone = "UTC";

    @Column(nullable = false)
    @Builder.Default
    private String currencySymbol = "₹";

    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String profilePicture;

    @Column
    private String country;

    @Column
    private java.time.LocalDate dateOfBirth;

    @ManyToOne
    @JoinColumn(name = "household_id")
    @JsonIgnoreProperties({"members", "admin"})
    private Household household;



    @JsonProperty("householdAdmin")
    public boolean isHouseholdAdmin() {
        return household != null && household.getAdmin() != null && 
               household.getAdmin().getId().equals(id);
    }

    @PrePersist
    public void prePersist() {
        if (timeZone == null) {
            timeZone = "UTC";
        }
        if (currencySymbol == null) {
            currencySymbol = "₹";
        }
    }

}
