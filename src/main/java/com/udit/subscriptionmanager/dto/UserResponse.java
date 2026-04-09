package com.udit.subscriptionmanager.dto;

import com.udit.subscriptionmanager.entity.User;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String profilePicture;
    private Long householdId;
    private String householdName;
    private boolean householdAdmin;

    public static UserResponse fromUser(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .profilePicture(user.getProfilePicture())
                .householdId(user.getHousehold() != null ? user.getHousehold().getId() : null)
                .householdName(user.getHousehold() != null ? user.getHousehold().getName() : null)
                .householdAdmin(user.getHousehold() != null
                        && user.getHousehold().getAdmin() != null
                        && user.getHousehold().getAdmin().getId().equals(user.getId()))
                .build();
    }
}
