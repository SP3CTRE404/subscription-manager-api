package com.udit.subscriptionmanager.controller;

import com.udit.subscriptionmanager.dto.NotificationResponse;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.service.NotificationService;
import com.udit.subscriptionmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    private User getLoggedInUser(Authentication authentication) {
        String email = authentication.getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Logged in user not found"));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<NotificationResponse>> getPendingNotifications(Authentication authentication) {
        User user = getLoggedInUser(authentication);
        List<NotificationResponse> pending = notificationService.getPendingNotifications(user)
                .stream()
                .map(NotificationResponse::fromNotification)
                .toList();
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long id,
            Authentication authentication) {
        User user = getLoggedInUser(authentication);
        notificationService.markAsRead(user, id);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read successfully."));
    }
}
