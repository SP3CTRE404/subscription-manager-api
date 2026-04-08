package com.udit.subscriptionmanager.controller;

import com.udit.subscriptionmanager.dto.ChangePasswordRequest;
import com.udit.subscriptionmanager.dto.ProfileUpdateRequest;
import com.udit.subscriptionmanager.dto.UserResponse;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    // --- Helper: extract logged-in User from JWT ---
    private User getLoggedInUser(Authentication authentication) {
        String email = authentication.getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Logged in user not found"));
    }

    // --- GET /api/users/profile --- Get current user's profile
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        User user = getLoggedInUser(authentication);
        return ResponseEntity.ok(UserResponse.fromUser(user));
    }

    // --- PUT /api/users/profile --- Update profile (name, phone)
    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        User user = getLoggedInUser(authentication);
        User updated = userService.updateProfile(user, request);
        return ResponseEntity.ok(UserResponse.fromUser(updated));
    }

    // --- PUT /api/users/password --- Change password
    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        User user = getLoggedInUser(authentication);
        userService.changePassword(user, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }
}
