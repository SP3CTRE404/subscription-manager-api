package com.udit.subscriptionmanager.controller;

import com.udit.subscriptionmanager.dto.HouseholdResponse;
import com.udit.subscriptionmanager.dto.MemberResponse;
import com.udit.subscriptionmanager.dto.SubscriptionResponse;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.service.HouseholdService;
import com.udit.subscriptionmanager.service.SubscriptionService;
import com.udit.subscriptionmanager.service.UserService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;
    private final SubscriptionService subscriptionService;
    private final UserService userService;

    // --- Helper: extract logged-in User from JWT ---
    private User getLoggedInUser(Authentication authentication) {
        String email = authentication.getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Logged in user not found"));
    }

    // --- POST /api/households --- Create a new household
    @PostMapping
    public ResponseEntity<HouseholdResponse> createHousehold(
            @RequestBody CreateHouseholdRequest request,
            Authentication authentication) {
        User user = getLoggedInUser(authentication);
        return ResponseEntity.ok(householdService.createHousehold(user, request.getName()));
    }

    // --- POST /api/households/join --- Join a household via invite code
    @PostMapping("/join")
    public ResponseEntity<HouseholdResponse> joinHousehold(
            @RequestBody JoinHouseholdRequest request,
            Authentication authentication) {
        User user = getLoggedInUser(authentication);
        return ResponseEntity.ok(householdService.joinHousehold(user, request.getInviteCode()));
    }

    // --- POST /api/households/leave --- Leave current household
    @PostMapping("/leave")
    public ResponseEntity<Map<String, String>> leaveHousehold(Authentication authentication) {
        User user = getLoggedInUser(authentication);
        householdService.leaveHousehold(user);
        return ResponseEntity.ok(Map.of("message", "Successfully left the household."));
    }

    // --- POST /api/households/transfer-admin --- Transfer admin role
    @PostMapping("/transfer-admin")
    public ResponseEntity<HouseholdResponse> transferAdmin(
            @RequestBody TransferAdminRequest request,
            Authentication authentication) {
        User user = getLoggedInUser(authentication);
        return ResponseEntity.ok(householdService.transferAdmin(user, request.getNewAdminId()));
    }

    // --- DELETE /api/households --- Delete household (admin only)
    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteHousehold(Authentication authentication) {
        User user = getLoggedInUser(authentication);
        householdService.deleteHousehold(user);
        return ResponseEntity.ok(Map.of("message", "Household deleted successfully."));
    }

    // --- PUT /api/households/name --- Edit household name (admin only)
    @PutMapping("/name")
    public ResponseEntity<HouseholdResponse> editHouseholdName(
            @RequestBody EditNameRequest request,
            Authentication authentication) {
        User user = getLoggedInUser(authentication);
        return ResponseEntity.ok(householdService.editName(user, request.getName()));
    }

    // --- POST /api/households/regenerate-invite --- Regenerate invite code (admin only)
    @PostMapping("/regenerate-invite")
    public ResponseEntity<HouseholdResponse> regenerateInviteCode(Authentication authentication) {
        User user = getLoggedInUser(authentication);
        return ResponseEntity.ok(householdService.regenerateInviteCode(user));
    }

    // --- GET /api/households/members --- Get household members
    @GetMapping("/members")
    public ResponseEntity<List<MemberResponse>> getMembers(Authentication authentication) {
        User user = getLoggedInUser(authentication);
        if (user.getHousehold() == null) {
            throw new RuntimeException("User does not belong to any household.");
        }
        return ResponseEntity.ok(householdService.getMembers(user.getHousehold().getId()));
    }

    // --- GET /api/households/members/{memberId}/subscriptions --- Get a member's subscriptions
    @GetMapping("/members/{memberId}/subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> getMemberSubscriptions(
            @PathVariable Long memberId,
            Authentication authentication) {
        User user = getLoggedInUser(authentication);
        if (user.getHousehold() == null) {
            throw new RuntimeException("User does not belong to any household.");
        }
        // Verify the target member is in the same household
        User member = userService.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        if (member.getHousehold() == null || member.getHousehold().getId() != user.getHousehold().getId()) {
            throw new RuntimeException("That user is not in your household.");
        }
        return ResponseEntity.ok(subscriptionService.getAllSubscriptionsForUser(memberId));
    }

    // --- GET /api/households/my --- Get current user's household details
    @GetMapping("/my")
    public ResponseEntity<HouseholdResponse> getMyHousehold(Authentication authentication) {
        User user = getLoggedInUser(authentication);
        return ResponseEntity.ok(householdService.getHouseholdForUser(user));
    }

    // --- Inner request DTOs ---

    @Getter @Setter
    public static class CreateHouseholdRequest {
        private String name;
    }

    @Getter @Setter
    public static class JoinHouseholdRequest {
        private String inviteCode;
    }

    @Getter @Setter
    public static class TransferAdminRequest {
        private Long newAdminId;
    }

    @Getter @Setter
    public static class EditNameRequest {
        private String name;
    }
}
