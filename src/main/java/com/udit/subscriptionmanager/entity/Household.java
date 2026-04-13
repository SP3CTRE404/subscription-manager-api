package com.udit.subscriptionmanager.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "households")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "household_gen")
    @SequenceGenerator(name = "household_gen", sequenceName = "household_id_seq", initialValue = 100000000, allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(unique = true)
    private String inviteCode;

    @Column(columnDefinition = "TEXT")
    private String imageUrl; // NEW: Field to store household image

    @OneToOne
    @JoinColumn(name = "admin_id")
    @JsonBackReference
    private User admin;

    @OneToMany(mappedBy = "household")
    @JsonManagedReference
    private List<User> members;
}