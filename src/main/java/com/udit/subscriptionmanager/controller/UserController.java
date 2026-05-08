package com.udit.subscriptionmanager.controller;

import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.security.CustomUserDetails;
import com.udit.subscriptionmanager.security.CustomUserDetailsService;
import com.udit.subscriptionmanager.security.JwtService;
import com.udit.subscriptionmanager.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(request.getEmail());
        var jwtToken = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(new LoginResponse(jwtToken, userDetails.getUser()));
    }

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody RegisterRequest request) {

        User savedUser = userService.registerUser(
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.getDateOfBirth(),
                request.isCreateHousehold(),
                request.getHouseholdName(),
                request.getCurrencySymbol(),
                request.getCountry()
        );

        return ResponseEntity.ok(savedUser);
    }

    @Setter
    @Getter
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Setter
    @Getter
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private User user;
    }

    // DTO for request
    @Setter
    @Getter
    public static class RegisterRequest {
        private String email;
        private String password;
        private String fullName;
        private java.time.LocalDate dateOfBirth;
        private boolean createHousehold;
        private String householdName;
        private String currencySymbol;
        private String country;
    }
}