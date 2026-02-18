package com.udit.subscriptionmanager.controller;

import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.service.UserService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody RegisterRequest request) {

        User savedUser = userService.registerUser(
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.isCreateHousehold(),
                request.getHouseholdName()
        );

        return ResponseEntity.ok(savedUser);
    }

    // DTO for request
    @Setter
    @Getter
    public static class RegisterRequest {
        private String email;
        private String password;
        private String fullName;

        private boolean createHousehold;
        private String householdName;

    }
}