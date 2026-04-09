package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.dto.ProfileUpdateRequest;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.entity.Household;
import com.udit.subscriptionmanager.repository.HouseholdRepository;
import com.udit.subscriptionmanager.repository.UserRepository;
import com.udit.subscriptionmanager.exception.BadRequestException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;
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
            throw new BadRequestException("Email already registered");
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
                throw new BadRequestException("Household name is required");
            }

            Household household = Household.builder()
                    .name(householdName)
                    .createdAt(LocalDateTime.now())
                    .admin(savedUser)
                    .inviteCode(java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase())
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

    public Optional<User> findById(@NonNull Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User updateProfile(User user, ProfileUpdateRequest request) {
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getProfilePicture() != null) {
            user.setProfilePicture(request.getProfilePicture());
        }
        return userRepository.save(java.util.Objects.requireNonNull(user));
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}