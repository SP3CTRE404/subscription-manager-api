package com.udit.subscriptionmanager.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdResponse {
    private Long id;
    private String name;
    private String inviteCode;
    private Long adminId;
    private String adminName;
    private LocalDateTime createdAt;
    private List<MemberResponse> members;
}
