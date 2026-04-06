package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.entity.Household;
import com.udit.subscriptionmanager.repository.HouseholdRepository;
import com.udit.subscriptionmanager.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(String email,
                             String password,
                             String fullName,
                             boolean createHousehold,
                             String householdName) {

        // prevent duplicate email
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))   // hash password
                .fullName(fullName)
                .createdAt(LocalDateTime.now())
                .build();

        // save user first
        User savedUser = userRepository.save(java.util.Objects.requireNonNull(user));

        // CASE 1: create household + admin
        if (createHousehold) {

            if (householdName == null || householdName.isBlank()) {
                throw new RuntimeException("Household name is required");
            }

            Household household = Household.builder()
                    .name(householdName)
                    .createdAt(LocalDateTime.now())
                    .admin(savedUser)
                    .build();

            Household savedHousehold = householdRepository.save(java.util.Objects.requireNonNull(household));

            savedUser.setHousehold(savedHousehold);

            return userRepository.save(savedUser);
        }

        // CASE 2: solo user
        return savedUser;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}