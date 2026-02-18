package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.entity.Household;
import com.udit.subscriptionmanager.repository.HouseholdRepository;
import com.udit.subscriptionmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;


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
                .password(password) // hashing later
                .fullName(fullName)
                .createdAt(LocalDateTime.now())
                .build();

        // CASE 1: create household + admin
        if (createHousehold) {

            if (householdName == null || householdName.isBlank()) {
                throw new RuntimeException("Household name is required");
            }

            // save user first (needed for FK)
            User savedUser = userRepository.save(user);

            Household household = Household.builder()
                    .name(householdName)
                    .createdAt(LocalDateTime.now())
                    .admin(savedUser)
                    .build();

            Household savedHousehold = householdRepository.save(household);

            // link user back to household
            savedUser.setHousehold(savedHousehold);

            return userRepository.save(savedUser);
        }

        // CASE 2: solo user
        return userRepository.save(user);
    }


    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
