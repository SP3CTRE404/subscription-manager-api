package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.dto.ProfileUpdateRequest;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.entity.Household;
import com.udit.subscriptionmanager.repository.HouseholdRepository;
import com.udit.subscriptionmanager.repository.UserRepository;
import com.udit.subscriptionmanager.repository.SubscriptionRepository;
import com.udit.subscriptionmanager.repository.SubscriptionHistoryRepository;
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
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository subscriptionHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(String email,
                             String password,
                             String fullName,
                             java.time.LocalDate dateOfBirth,
                             boolean createHousehold,
                             String householdName,
                             String currencySymbol,
                             String country) {

        // prevent duplicate email
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already registered");
        }

        // Age check for households
        if (dateOfBirth != null) {
            int age = java.time.Period.between(dateOfBirth, java.time.LocalDate.now()).getYears();
            if (age < 18 && createHousehold) {
                throw new BadRequestException("Users under 18 cannot create a household.");
            }
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))   // hash password
                .fullName(fullName)
                .dateOfBirth(dateOfBirth)
                .createdAt(LocalDateTime.now())
                .currencySymbol(currencySymbol != null ? currencySymbol : "₹")
                .country(country)
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
        if (request.getCurrencySymbol() != null && !request.getCurrencySymbol().isBlank()) {
            user.setCurrencySymbol(request.getCurrencySymbol());
        }
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            user.setCountry(request.getCountry());
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

    @Transactional
    public void deleteUser(User user) {
        // 1. Delete subscription histories and personal subscriptions
        subscriptionHistoryRepository.deleteBySubscriptionUserId(user.getId());
        subscriptionRepository.deleteByUserId(user.getId());

        // 2. Handle Household association
        Household household = user.getHousehold();
        if (household != null) {
            // Check if user is admin
            if (user.isHouseholdAdmin()) {
                // If admin, we check if there are other members
                if (household.getMembers().size() <= 1) {
                    // Solo household admin -> Delete household entirely
                    
                    // BREAK CIRCULAR REFERENCE FIRST!
                    household.setAdmin(null);
                    householdRepository.save(household);
                    
                    user.setHousehold(null);
                    userRepository.save(user);

                    // Delete history for all household subscriptions
                    subscriptionHistoryRepository.deleteBySubscriptionHouseholdId(household.getId());
                    subscriptionRepository.deleteByHouseholdId(household.getId());
                    householdRepository.delete(household);
                } else {
                    // Multiple members -> This case should be handled by frontend (Admin Transfer)
                    household.setAdmin(null);
                    householdRepository.save(household);
                    user.setHousehold(null);
                    userRepository.save(user);
                }
            } else {
                // Regular member -> Just leave
                user.setHousehold(null);
                userRepository.save(user);
            }
        }

        // 3. Delete user
        userRepository.delete(user);
    }

    @Transactional
    public void resetPassword(User user, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}