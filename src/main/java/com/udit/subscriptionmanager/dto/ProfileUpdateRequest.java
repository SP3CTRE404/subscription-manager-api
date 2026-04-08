package com.udit.subscriptionmanager.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateRequest {
    private String fullName;
    private String phoneNumber;
}
